package org.fisheep.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexUtil {


    final static String regex = ".* (?<score>\\d+)分.*";
    final static Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE);

    public static String parseScore(String explain) {
        Matcher matcher = pattern.matcher(explain);
        String score = "-";
        if (matcher.find()) {
            score = matcher.group("score");
        }
        return score;
    }

}
