package pt.haslab.alloyaddons;

import edu.mit.csail.sdg.alloy4.A4Reporter;
import edu.mit.csail.sdg.alloy4.Err;
import edu.mit.csail.sdg.alloy4viz.AlloyInstance;
import edu.mit.csail.sdg.alloy4viz.StaticInstanceReader;
import edu.mit.csail.sdg.ast.Expr;
import edu.mit.csail.sdg.parser.CompModule;
import edu.mit.csail.sdg.parser.CompUtil;
import edu.mit.csail.sdg.translator.A4Solution;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

public interface ParseUtil {

    static CompModule parseModel(String model) throws UncheckedIOException, Err {
        return parseModel(model, A4Reporter.NOP);
    }

    static CompletableFuture<CompModule> parseModelAsync(String model) throws UncheckedIOException, Err {
        return CompletableFuture.supplyAsync(() -> parseModel(model, A4Reporter.NOP));
    }

    static CompModule parseModel(String model, A4Reporter rep) throws UncheckedIOException, Err {
        try {
            String prefix_name = "thr-%d.alloy_heredoc".formatted(Thread.currentThread().getId());
            File file = File.createTempFile(prefix_name, ".als");
            file.deleteOnExit();

            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(file.toPath()))) {
                out.write(model.getBytes());
                out.flush();
            }
            return CompUtil.parseEverything_fromFile(rep, null, file.getAbsolutePath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static AlloyInstance parseInstance(A4Solution solution) throws UncheckedIOException, Err {
        return parseInstance(solution, 0);
    }

    static AlloyInstance parseInstance(A4Solution solution, Integer state) throws UncheckedIOException, Err {
        try {
            String prefix_name = "thr-%d.a4f".formatted(Thread.currentThread().getId());
            File file = File.createTempFile(prefix_name, ".als");
            file.deleteOnExit();
            solution.writeXML(file.getAbsolutePath());

            return StaticInstanceReader.parseInstance(file.getAbsoluteFile(), state);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<AlloyInstance> parseInstances(A4Solution solution, Integer count) throws UncheckedIOException, Err {
        return parseInstances(solution, 0, count);
    }

    static List<AlloyInstance> parseInstances(A4Solution solution, Integer from, Integer to) throws UncheckedIOException, Err {
        try {
            String prefix_name = "thr-%d.a4f".formatted(Thread.currentThread().getId());
            File file = File.createTempFile(prefix_name, ".als");
            file.deleteOnExit();
            solution.writeXML(file.getAbsolutePath());

            return IntStream.range(from, to).boxed().map(i -> StaticInstanceReader.parseInstance(file.getAbsoluteFile(), i)).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Expr parseOneExprFromString(CompModule world, String value) {
        try {
            return world.parseOneExpressionFromString(value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String lineCSV(String sep, List<String> strings) {
        if (strings == null || strings.isEmpty())
            return "";
        StringBuilder res = new StringBuilder();
        String last = strings.get(strings.size() - 1);
        for (int i = 0; i < strings.size() - 1; i++) res.append(strings.get(i)).append(sep);

        return res.append(last).toString();
    }
}
