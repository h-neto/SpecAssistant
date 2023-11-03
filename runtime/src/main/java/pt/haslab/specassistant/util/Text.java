package pt.haslab.specassistant.util;

import edu.mit.csail.sdg.alloy4.Err;
import edu.mit.csail.sdg.alloy4.Pos;
import edu.mit.csail.sdg.parser.CompModule;
import pt.haslab.alloyaddons.ParseUtil;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static pt.haslab.alloyaddons.AlloyUtil.offsetsToPos;

public interface Text {

    String tag_regex = "//SECRET";

    String options_regex = "TARGETS\\s+?((?:\\w+)+)";
    String tag_opt_regex = tag_regex + "\\s+" + options_regex;

    String pgs = "sig|fact|assert|check|fun|pred|run";

    String pgp = "var|one|abstract|lone|some";

    String pgd = "(?:(?:" + pgp + ")\\s+)*?" + pgs;

    String comment = "/\\*(?:.|\\n)*?\\*/\\s*|//.*\\n|--.*\\n";

    String secret_prefix = "(" + tag_regex + "\\s*?\\n\\s*(?:" + comment + ")*?\\s*)(?: " + pgd + ")";
    String block_end = "(?:" + tag_regex + "\\s*?\\n\\s*)?(?:(?:" + comment + ")*?\\s*(?:" + pgd + ")\\s|$)";
    String secret = tag_regex + "\\s*?\\n\\s*(?:" + comment + ")*?\\s*((?:" + pgd + ")(?:.|\\n)*?)" + block_end;

    static List<Integer> getSecretPositions(String code) {
        List<Integer> result = new ArrayList<>();
        Pattern p = Pattern.compile(secret_prefix);
        Matcher m = p.matcher(code);

        for (int i = 0; m.find(i); i = m.end(1)) {
            result.add(m.end(1));
        }

        return result;
    }

    static String extractSecrets(String code) {
        Pattern p = Pattern.compile(secret);
        Matcher m = p.matcher(code);
        StringBuilder result = new StringBuilder();

        for (int i = 0; m.find(i); i = m.end(1))
            result.append(m.group(1).strip()).append("\n");

        return result.toString();
    }

    static boolean containsSecrets(String code) {
        Pattern p = Pattern.compile(secret);
        Matcher m = p.matcher(code);
        return  m.find();
    }

    static LocalDateTime parseDate(String dateString) {
        dateString = dateString.replaceAll(",", "").replaceAll("/", "-").strip();
        if (dateString.matches(".*?(?i:pm|am)")) {
            if (dateString.matches("^\\d{4}.*")) { //Start With Year
                return LocalDateTime.parse(dateString, DateTimeFormatter.ofPattern("yyyy-M-d h:m:s a").withLocale(Locale.ENGLISH));
            } else if (dateString.matches("\\d{1,2}.*")) { //Start With Month
                return LocalDateTime.parse(dateString, DateTimeFormatter.ofPattern("M-d-yyyy h:m:s a").withLocale(Locale.ENGLISH));
            }
        } else {
            if (dateString.matches("^\\d{4}.*")) { //Start With Year
                return LocalDateTime.parse(dateString, DateTimeFormatter.ofPattern("yyyy-M-d H:m:s"));
            } else if (dateString.matches("\\d{1,2}.*")) { //Start With Month
                return LocalDateTime.parse(dateString, DateTimeFormatter.ofPattern("M-d-yyyy H:m:s"));
            }
        }
        throw new IllegalArgumentException("Invalid date format: " + dateString);
    }

    static List<Pos> secretPos(String code) {
        return offsetsToPos(code, Text.getSecretPositions(code));
    }

    static List<Pos> secretPos(String filename, String code) {
        return offsetsToPos(filename, code, Text.getSecretPositions(code));
    }

    static CompModule parseModelWithSecrets(String secrets, String code) {
        CompModule w;
        try {
            w = ParseUtil.parseModel(code + "\n" + secrets);
        } catch (Err e) {
            if (containsSecrets(code)) w = ParseUtil.parseModel(code);
            else throw e;
        }
        return w;
    }
}
