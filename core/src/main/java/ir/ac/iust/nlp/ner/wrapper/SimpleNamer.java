package ir.ac.iust.nlp.ner.wrapper;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by majid on 1/10/15.
 */
public class SimpleNamer {

    //tag => (first split, {other splits})
    static private HashMap<String, HashMap<String, ArrayList<String[]>>> tagNames = new HashMap<>();
    static private HashMap<String, Path> gazeteers = new HashMap<>();
    static private HashMap<String, Boolean> participate = new HashMap<>();

    public static boolean add(String tag, String name) throws IOException {
        if (!gazeteers.containsKey(tag)) return false;
        HashMap<String, ArrayList<String[]>> names = tagNames.get(tag);
        boolean success = addToList(participate.get(tag), names, name);
        if (!success) return false;
        Path path = gazeteers.get(tag);
        String content = new String(Files.readAllBytes(path), Charset.forName("UTF-8"));
        content += (name + "\r\n");
        Files.write(path, content.getBytes("UTF-8"), StandardOpenOption.WRITE);
        return true;
    }

    public static HashMap<String, ArrayList<String[]>> prepare(Path gazeteer, String tag,
                                                               boolean participateOneWordNames) throws IOException {
        HashMap<String, ArrayList<String[]>> names = tagNames.get(tag);
        if (names != null) return names;
        names = new HashMap<>();
        List<String> lines = Files.readAllLines(gazeteer, Charset.forName("UTF-8"));
        for (String line : lines) {
            addToList(participateOneWordNames, names, line);
        }
        gazeteers.put(tag, gazeteer);
        tagNames.put(tag, names);
        participate.put(tag, participateOneWordNames);
        return names;
    }

    private static boolean addToList(boolean participateOneWordNames, HashMap<String, ArrayList<String[]>> names, String line) {
        if (line.trim().length() == 0) return false;
        String[] allSplits = line.trim().split("\\s+");
        String[] otherSplits;
        if (participateOneWordNames && allSplits.length == 1 && allSplits[0].trim().length() == 0) return false;
        else if (participateOneWordNames && allSplits.length == 1 && allSplits[0].trim().length() > 0)
            otherSplits = new String[]{""};
        else if (allSplits.length == 1) return false;
        else {
            otherSplits = new String[allSplits.length - 1];
            System.arraycopy(allSplits, 1, otherSplits, 0, otherSplits.length);
        }
        ArrayList<String[]> value = names.get(allSplits[0]);
        if (value == null) {
            value = new ArrayList<>();
            names.put(allSplits[0], value);
        }
        value.add(otherSplits);
        return true;
    }

    public static void manipulateFile(Path file, String tag) throws IOException {
        HashMap<String, ArrayList<String[]>> names = tagNames.get(tag);
        List<String> lines = Files.readAllLines(file, Charset.forName("UTF-8"));
        StringBuilder builder = new StringBuilder();
        int placeIPerson = 0;
        for (int i = 0; i < lines.size() - 1; i++) {
            String line = lines.get(i);
            String[] splits = line.split("\t");
            if (splits.length != 2) continue;
            if (placeIPerson > 0) {
                builder.append(splits[0]).append("\tI-").append(tag).append("\r\n");
                placeIPerson--;
                continue;
            }
            ArrayList<String[]> matches = names.get(splits[0]);
            if (matches == null) {
                builder.append(line).append("\r\n");
                continue;
            }
            boolean oneMathcedFound = false;
            for (String[] match : matches) {
                if (lines.size() - i > match.length) {
                    boolean matched = true;
                    for (int j = 0; j < match.length; j++)
                        if (!lines.get(i + j + 1).startsWith(match[j])) {
                            matched = false;
                            break;
                        }
                    if (matched) {
                        builder.append(splits[0]).append("\tB-").append(tag).append("\r\n");
                        if (match.length != 1 || match[0].length() > 0)
                            placeIPerson = match.length;
                        oneMathcedFound = true;
                        break;
                    }
                }
            }
            if (!oneMathcedFound)
                builder.append(line).append("\r\n");
        }
        Files.write(file, builder.toString().getBytes("UTF-8"));
    }
}
