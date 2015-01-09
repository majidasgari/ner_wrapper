package ir.ac.iust.nlp.ner.wrapper;

import ir.ac.iust.nlp.ner.wrapper.io.FileHandler;
import iust.nlp.nlppack.Main;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Created by maJid~ASGARI on 1/9/2015.
 */
public class Runner {

    private static void prepareFiles() {
        FileHandler.setCopyRoot("resources");
        String[] files = new String[]{"crf", "hunpos-tag", "hunpos-train"};
        FileHandler.prepareFile("linux", files);
        files = new String[]{"crf.exe", "crf.pdb", "cygwin1.dll", "hunpos-tag.exe", "hunpos-train.exe"};
        FileHandler.prepareFile("windows", files);
        files = new String[]{"model.txt", "option.txt"};
        FileHandler.prepareFile("named_ner", files);
        FileHandler.prepareFile("normal_ner", files);
        FileHandler.prepareFile("pos", "model_98_accuracy");
        FileHandler.prepareFile(".", "organizations", "persian names", "sample.txt", "features");
    }

    public static void main(final String[] args) throws IOException, URISyntaxException {
        prepareFiles();
        try {
            namedEntityRecognize(false, Paths.get("resources", "sample.txt"));
        } catch (Exception exp) {
            exp.printStackTrace();
        }
    }

    private static String[] array(String... input) {
        return input;
    }

    public static void namedEntityRecognize(boolean named, Path path) throws IOException {
        Path input = Paths.get(path.toString() + "_folder", path.toFile().getName());
        if (!Files.exists(input.getParent()))
            Files.createDirectories(input.getParent());
        Files.copy(path, input, StandardCopyOption.REPLACE_EXISTING);
//        Files.copy(Paths.get("feature"),
//                Paths.get(input.getParent().toString(), "feature"), StandardCopyOption.REPLACE_EXISTING);
        String pathAddress = input.toFile().getAbsolutePath();
        String outputPathAddress = pathAddress + ".out";
        Main.main(array("filemixer", "i1=" + pathAddress, "i2=" + pathAddress,
                "o=" + pathAddress + ".out", "c=0:0"));
        String TAGGED = pathAddress + ".pos";
        runCommand("hunpos-tag", FileHandler.getPath("pos", "model_98_accuracy").toString(),
                "<", outputPathAddress, ">", TAGGED);
        String FIXED = pathAddress + ".fixed";
        Main.main(array("postagfixer", "i=" + TAGGED, "o=" + FIXED));
        String TRANS = pathAddress + ".trans";
        Main.main(array("transliterator", "i=" + FIXED, "o=" + TRANS, "l=true", "m=true"));
        String FARSI_NORM = pathAddress + ".fnorm";
        Main.main(array("sentencemarker", "i=" + FIXED, "o=" + FARSI_NORM));
        String NORM = pathAddress + ".norm";
        Main.main(array("sentencemarker", "i=" + TRANS, "o=" + NORM));
        String CRF_DATA = Paths.get(input.getParent().toString(), "data.untagged").toString();
        if (named) {
            String NAMED = pathAddress + ".named";
            Main.main(array("pnamemarker", "i=" + NORM, "o=" + NAMED, "cc=2", "tc=1", "dc=0", "-t"));
            Main.main(array("NerFeatureGenerator", "-ulb", NAMED, CRF_DATA, "no"));
        } else
            Main.main(array("NerFeatureGenerator", "-ilb", NORM, CRF_DATA, "no"));
        String MODEL_FOLDER = named ? Paths.get("resources", "named_ner").toString()
                : Paths.get("resources", "normal_ner").toString();
        runCommand("crf", "-prd", "-d", MODEL_FOLDER, "-o", "option.txt");

        String UNTAGGED = pathAddress + ".untagged";
        Files.move(Paths.get(MODEL_FOLDER, "data.untagged"), Paths.get(UNTAGGED), StandardCopyOption.REPLACE_EXISTING);
        String MODEL = Paths.get(MODEL_FOLDER, "data.untagged.model").toString();
        String FARSI_MODEL = pathAddress + ".out";
        runCommand("filemixer", "i=" + FARSI_NORM, "i2=" + MODEL, "o=" + FARSI_MODEL, "c=0:0;1:-1");
    }

    private static void runCommand(String... commands) throws IOException {
        runCommandInWindows(commands);
    }

    private static void runCommandInWindows(String... commands) throws IOException {
        commands[0] = FileHandler.getPath("windows", commands[0] + ".exe").toString();
        String[] commandsToCmd = new String[commands.length + 2];
        commandsToCmd[0] = "CMD";
        commandsToCmd[1] = "/C";
        System.arraycopy(commands, 0, commandsToCmd, 2, commands.length);
        ProcessBuilder probuilder = new ProcessBuilder(commandsToCmd);
        Process process = probuilder.start();
        try {
            process.waitFor();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
