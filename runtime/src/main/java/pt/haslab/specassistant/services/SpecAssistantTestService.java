package pt.haslab.specassistant.services;

import edu.mit.csail.sdg.alloy4.Err;
import edu.mit.csail.sdg.ast.Command;
import edu.mit.csail.sdg.ast.Func;
import edu.mit.csail.sdg.parser.CompModule;
import edu.mit.csail.sdg.translator.A4Options;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.SneakyThrows;
import org.bson.types.ObjectId;
import org.jboss.logging.Logger;
import pt.haslab.Repairer;
import pt.haslab.alloyaddons.AlloyUtil;
import pt.haslab.specassistant.data.aggregation.Transition;
import pt.haslab.specassistant.data.models.*;
import pt.haslab.specassistant.data.policy.PolicyOption;
import pt.haslab.specassistant.repositories.ChallengeRepository;
import pt.haslab.specassistant.repositories.ModelRepository;
import pt.haslab.specassistant.repositories.TestRepository;
import pt.haslab.specassistant.util.FutureUtil;
import pt.haslab.specassistant.util.Text;

import java.io.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;


@ApplicationScoped
public class SpecAssistantTestService {
    @Inject
    Logger log;

    @Inject
    ModelRepository modelRepo;

    @Inject
    ChallengeRepository challengeRepo;

    @Inject
    HintGenerator hintGenerator;

    @Inject
    TestRepository testRepo;

    @Inject
    GraphManager graphManager;

    @Inject
    GraphIngestor graphIngestor;

    @Inject
    PolicyManager policyManager;

    // TAR TESTS *******************************************************************************************
    private static final long TarTimeoutSeconds = 60;

    public CompletableFuture<SpecAssistantTest.Data> doTarTest(CompModule world, Challenge challenge) {
        return CompletableFuture.supplyAsync(() -> {
            Collection<Func> repairTargets = AlloyUtil.getFuncsWithNames(world.getAllFunc().makeConstList(), challenge.getTargetFunctions());
            Command command = challenge.getValidCommand(world, challenge.getCmd_n()).orElseThrow();
            Repairer r = Repairer.make(world, command, repairTargets, 2);

            long t = System.nanoTime();
            boolean b = r.repair().isPresent();

            return new SpecAssistantTest.Data(b, System.nanoTime() - t);
        }).completeOnTimeout(new SpecAssistantTest.Data(false, (double) TarTimeoutSeconds), TarTimeoutSeconds, TimeUnit.SECONDS);
    }


    public CompletableFuture<Void> testChallengeWithTAR(String modelId, Predicate<Model> model_filter) {
        log.trace("Starting TAR test for challenge " + modelId);

        final String secrets = "\n" + Text.extractSecrets(modelRepo.findById(modelId).getCode());
        Repairer.opts.solver = A4Options.SatSolver.SAT4J;

        Map<String, Challenge> challengeMap = challengeRepo.findByModelIdAsCmdMap(modelId);

        return FutureUtil.runEachAsync(modelRepo.streamByOriginalAndUnSat(modelId).filter(x -> testRepo.findByIdOptional(new SpecAssistantTest.ID(x.getId(), "TAR")).map(y -> !y.getData().success() && y.getData().time() > 60.0).orElse(true)).filter(model_filter), m -> {
            CompModule w;
            try {
                w = Text.parseModelWithSecrets(secrets, m.getCode());
                Challenge ex = challengeMap.get(m.getCmd_n());
                if (ex != null && ex.isValidCommand(w, m.getCmd_i())) {
                    return this.doTarTest(w, ex).thenApply(d -> {
                        if (d.time() > 60.0) log.warn("YIKES " + d.time());
                        return d;
                    }).thenAccept(d -> testRepo.updateOrCreate(new SpecAssistantTest.ID(m.getId(), "TAR"), challengeMap.get(m.getCmd_n()).getGraph_id(), d));

                }
            } catch (Err e) {
                log.error("Error while parsing model " + m.getId() + ": " + e.getClass().getSimpleName() + ": " + e.getMessage());
            } catch (Exception e) {
                e.printStackTrace();
            }
            return CompletableFuture.completedFuture(null);
        }, FutureUtil.errorLog(log, "Failed to test a model")).whenComplete(FutureUtil.logTrace(log, "Completed TAR test on challenge " + modelId));
    }

    public CompletableFuture<Void> testAllChallengesWithTAR(Predicate<Model> model_filter) {
        return FutureUtil.forEachOrderedAsync(challengeRepo.getAllModelIds(), x -> this.testChallengeWithTAR(x, model_filter), FutureUtil.errorLog(log, "Failed to complete a model")).whenComplete(FutureUtil.logTrace(log, "Finished stressing all models with TAR"));
    }

    // SPEC TESTS ******************************************************************************************
    public SpecAssistantTest.Data getSpecTestMutationData(CompModule world, Challenge challenge) {
        long time = System.nanoTime();
        Optional<Node> node = hintGenerator.mutatedNextState(challenge, world);
        time = System.nanoTime() - time;
        return new SpecAssistantTest.Data(node.isPresent(), time, node.map(Node::getHopDistance).orElse(null));
    }

    public SpecAssistantTest.Data getSpecTestData(CompModule world, Challenge challenge) {
        long time = System.nanoTime();
        Optional<Transition> t = hintGenerator.worldTransition(challenge, world);
        time = System.nanoTime() - time;
        return new SpecAssistantTest.Data(t.isPresent(), time, t.map(x -> x.getTo().getHopDistance()).orElse(null), t.orElse(null));
    }

    private void specTestBare(Challenge challenge, CompModule world, Integer cmdI, String id, String preffix) {
        if (challenge != null && challenge.isValidCommand(world, cmdI)) {
            testRepo.updateOrCreate(new SpecAssistantTest.ID(id, preffix + "-SPEC"), challenge.getGraph_id(), getSpecTestData(world, challenge));
        }
    }

    private Consumer<String> specTestBareUncurry(Challenge challenge, CompModule world, Integer cmdI, String id) {
        return preffix -> specTestBare(challenge, world, cmdI, id, preffix);
    }

    private Consumer<String> specTestFullUncurry(Challenge challenge, CompModule world, Integer cmdI, String id) {
        return preffix -> {
            specTestBare(challenge, world, cmdI, id, preffix);
            if (Objects.equals(preffix, "TED"))
                specTestMutationBare(challenge, world, cmdI, id);
        };
    }

    private void specTestMutationBare(Challenge challenge, CompModule world, Integer cmdI, String id) {
        if (challenge != null && challenge.isValidCommand(world, cmdI)) {
            testRepo.updateOrCreate(new SpecAssistantTest.ID(id, "SPEC_MUTATION"), challenge.getGraph_id(), getSpecTestMutationData(world, challenge));
        }
    }

    public void remakeGraphManagement(Map<String, List<String>> map) {
        graphManager.dropEverything();
        map.forEach(this::makeGraphAndChallengesFromCommands);
        fixTestGraphIds();
    }


    public void specTestDefaultPolicies(Map<String, List<String>> map) {
        graphManager.dropEverything();
        testRepo.deleteTestsByNotType("TAR");
        map.forEach(this::makeGraphAndChallengesFromCommands);
        fixTestGraphIds();
        testal();
    }

    private void writeSetToFile(Set<String> stringSet, String filePath) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            for (String value : stringSet) {
                writer.write(value);
                writer.newLine(); // Write each value on a new line
            }
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Set<String> readSetFromFile(String filePath) {
        Set<String> stringSet = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stringSet.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return stringSet;
    }


    @SneakyThrows
    public void retrain() {
        Graph.findAll().project(Graph.class).stream().map(x -> x.id).forEach(graphManager::cleanGraph);
        Set<String> testing_dataset = readSetFromFile("testing.txt");
        Set<String> ms = challengeRepo.findAll().stream().map(Challenge::getModel_id).collect(Collectors.toSet());

        FutureUtil.forEachOrderedAsync(ms, md -> graphIngestor.parseModelTree(md, m -> !testing_dataset.contains(m.getId()))).get();
    }

    @SneakyThrows
    public void retest() {
        testRepo.deleteTestsByNotType("TAR");
        Set<String> testing_dataset = readSetFromFile("testing.txt");

        Log.trace("Parsing test dataset");

        Map<String, Map<String, Challenge>> mToCmdToC = challengeRepo.findAll().stream().collect(Collectors.groupingBy(Challenge::getModel_id, Collectors.toMap(Challenge::getCmd_n, x -> x)));

        Map<String, String> secrets = mToCmdToC.keySet().stream().map(modelRepo::findById).filter(Objects::nonNull).collect(Collectors.toMap(Model::getId, x -> Text.extractSecrets(x.getCode())));

        AtomicInteger i = new AtomicInteger();
        ConcurrentMap<Integer, Consumer<String>> runs = testing_dataset.stream().parallel().map(id -> {
            try {
                Model m = modelRepo.findByIdOptional(id).orElseThrow();
                Challenge challenge = mToCmdToC.get(m.getOriginal()).get(m.getCmd_n());
                CompModule world = Text.parseModelWithSecrets(secrets.get(challenge.getModel_id()), m.getCode());

                return specTestFullUncurry(challenge, world, m.getCmd_i(), m.getId());
            } catch (NoSuchElementException | Err e) {
                Log.error(e);
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toConcurrentMap(x -> i.getAndIncrement(), x -> x));


        Log.trace("Testing all");

        PolicyOption.samples.forEach((name, option) -> {
            Log.trace("Doing policy " + name + " : " + option);
            Graph.findAll().project(Graph.class).stream().parallel().forEach(x -> policyManager.computePolicyForGraph(x.getId(), option));
            Log.trace("Running tests " + option);
            runs.values().stream().parallel().forEach(r -> r.accept(name));
        });
    }


    @SneakyThrows
    private void testal() {
        Map<String, Set<String>> mToC = challengeRepo.findAll().stream().collect(Collectors.groupingBy(Challenge::getModel_id, Collectors.mapping(Challenge::getCmd_n, Collectors.toSet())));

        Log.trace("Splittig testing dataset");

        Map<String, Set<String>> mToSub = mToC.entrySet().stream().collect(Collectors.toConcurrentMap(Map.Entry::getKey, this::doPartition));

        Log.trace("Unifying testing dataset");

        Set<String> testing_dataset = mToSub.values().stream().flatMap(Collection::stream).collect(Collectors.toSet());

        writeSetToFile(testing_dataset, "testing.txt");

        Log.trace("Training models");

        FutureUtil.forEachAsync(mToSub.entrySet(), e -> graphIngestor.parseModelTree(e.getKey(), m -> !e.getValue().contains(m.getId()))).get();
    }

    private Set<String> doPartition(Map.Entry<String, Set<String>> e) {
        ConcurrentMap<String, List<Model>> batch = modelRepo.streamByDerivationOfAndOriginal(e.getKey(), e.getKey()).collect(Collectors.toConcurrentMap(Model::getId, List::of));

        Map<String, Map<String, Set<String>>> root_counts = batch.keySet().stream().collect(Collectors.toConcurrentMap(x -> x, x -> new HashMap<>()));
        root_counts.values().forEach(v -> e.getValue().forEach(c -> v.put(c, new HashSet<>())));

        while (!batch.isEmpty()) {
            new HashSet<>(batch.entrySet()).stream().parallel().forEach(r -> {
                Map<String, Set<String>> rs = root_counts.get(r.getKey());
                r.getValue()
                        .stream()
                        .filter(x -> Optional.ofNullable(x.getSat()).map(y -> y >= 0).orElse(false))
                        .forEach(x -> Optional.ofNullable(rs.get(x.getCmd_n())).ifPresent(y -> y.add(x.getId())));

                List<Model> next = modelRepo.streamByDerivationOfInAndOriginal(r.getValue().stream().map(Model::getId).toList(), e.getKey()).toList();
                if (next.isEmpty())
                    batch.remove(r.getKey());
                else batch.put(r.getKey(), next);
            });
        }

        Map<String, AtomicLong> integerMap = e.getValue().stream().collect(Collectors.toMap(x -> x, x -> new AtomicLong(0L)));
        root_counts.forEach((m, l) -> l.forEach((c, n) -> integerMap.get(c).addAndGet(n.size())));
        integerMap.values().forEach(x -> x.updateAndGet(l -> (long) (0.3 * l)));

        Set<String> ret = new HashSet<>();

        List<Map.Entry<String, Map<String, Set<String>>>> shuffle = new ArrayList<>(root_counts.entrySet());
        Collections.shuffle(shuffle);

        shuffle.forEach(e1 -> e1.getValue().forEach((c, l) -> {
            if (l.size() > 0 && integerMap.get(c).get() > 0) {
                integerMap.get(c).addAndGet(-l.size());
                ret.addAll(l);
            }
        }));

        return ret;
    }

    // AUTOSETUP *******************************************************************************************

    private static ObjectId getAGraphID(Map<String, ObjectId> graphspace, String prefix, String label) {
        if (!graphspace.containsKey(label)) graphspace.put(label, Graph.newGraph(prefix + "-" + label).id);
        return graphspace.get(label);
    }

    public void makeGraphAndChallengesFromCommands(String prefix, List<String> model_ids) {
        Map<String, ObjectId> graphspace = new HashMap<>();
        model_ids.forEach(id -> graphManager.generateChallengesWithGraphIdFromSecrets(l -> getAGraphID(graphspace, prefix, l), id));
    }

    public CompletableFuture<Void> autoSetupJob(List<String> model_ids, String prefix, Predicate<Model> model_filter) {
        AtomicLong start = new AtomicLong();
        return CompletableFuture
                .runAsync(() -> start.set(System.nanoTime()))
                .thenRun(() -> log.debug("Starting setup for " + prefix + " with model ids " + model_ids))
                .thenRun(() -> graphManager.deleteChallengesByModelIDs(model_ids, true))
                .thenRun(() -> makeGraphAndChallengesFromCommands(prefix, model_ids))
                .thenRun(() -> log.trace("Scanning models " + model_ids))
                .thenCompose(nil -> FutureUtil.allFutures(model_ids.stream().map(id -> graphIngestor.parseModelTree(id, model_filter))))
                .thenRun(() -> log.trace("Computing policies for " + prefix))
                .thenRun(() -> graphManager.getModelGraphs(model_ids.get(0)).forEach(id -> policyManager.computePolicyForGraph(id, PolicyOption.samples.get("TED"))))
                .thenRun(() -> log.debug("Completed setup for " + prefix + " with model ids " + model_ids + " in " + 1e-9 * (System.nanoTime() - start.get()) + " seconds"))
                .whenComplete(FutureUtil.log(log));
    }

    @SneakyThrows
    public void fixTestGraphIds() {
        FutureUtil.forEachAsync(testRepo.findAll().stream(), x -> {
            Model m = modelRepo.findById(x.getId().model_id());
            x.setGraphId(challengeRepo.findByModelIdAndCmdN(m.getOriginal(), m.getCmd_n()).orElseThrow().getGraph_id()).persistOrUpdate();
        }).get();
    }

    public CompletableFuture<Void> computePoliciesForAll(PolicyOption eval) {
        return FutureUtil.forEachAsync(Graph.findAll().stream().map(x -> (Graph) x), x -> policyManager.computePolicyForGraph(x.id, eval)).whenComplete(FutureUtil.logTrace(log, "Finished computing policies"));
    }

    @SneakyThrows
    public void genGraphs(String prefix, List<String> model_ids) {
        log.info("Starting setup for model ids " + model_ids);
        // Clean
        graphManager.deleteChallengesByModelIDs(model_ids, true);
        // Create graph
        makeGraphAndChallengesFromCommands(prefix, model_ids);
        // Fill graph
        FutureUtil.forEachAsync(model_ids, id -> graphIngestor.parseModelTree(id, x -> true)).whenComplete(FutureUtil.logTrace(log, "Setup Completed")).get();
    }

}