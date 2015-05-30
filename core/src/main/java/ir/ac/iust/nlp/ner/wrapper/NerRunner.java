package ir.ac.iust.nlp.ner.wrapper;

import ir.ac.iust.text.utils.FileHandler;
import ir.ac.iust.text.utils.LoggerUtils;
import ir.ac.iust.text.utils.NativeCommandRunner;
import iust.nlp.nlppack.Main;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Created by maJid~ASGARI on 1/9/2015.
 */
public class NerRunner {
    public static boolean prepared = false;
    private static Logger logger = LoggerUtils.getLogger(NerRunner.class, "ner-wrapper.log");

    private static void prepareFiles() throws IOException {
        if (prepared) return;
        FileHandler.setCopyRoot("resources");
        String[] files = new String[]{"crf", "hunpos-tag", "hunpos-train"};
        FileHandler.prepareFile("linux", files);
        files = new String[]{"crf.exe", "crf.pdb", "cygwin1.dll", "hunpos-tag.exe", "hunpos-train.exe"};
        FileHandler.prepareFile("windows", files);
        files = new String[]{"model.txt", "option.txt"};
        FileHandler.prepareFile("named_ner", files);
        FileHandler.prepareFile("normal_ner", files);
        FileHandler.prepareFile("pos", "model_98_accuracy");
        FileHandler.prepareFile(".", "organizations.txt", "persian_names.txt", "sample.txt", "features");
        if (File.separator.equals("/")) {
            for (File file : new File("resources/linux").listFiles()) {
                file.setExecutable(true);
            }
        }
        SimpleNamer.prepare(FileHandler.getPath(".", "persian_names.txt"), "PERS", true);
        SimpleNamer.prepare(FileHandler.getPath(".", "organizations.txt"), "ORG", true);
        prepared = true;
    }

    public static void main(final String[] args) throws IOException, URISyntaxException {
        try {
            namedEntityRecognize(false, Paths.get("resources", "sample.txt"));
        } catch (Exception exp) {
            exp.printStackTrace();
        }
    }

    private static String[] array(String... input) {
        return input;
    }

    public static Path prepareForFlexCrf(Path path) throws IOException, InterruptedException {
        return prepareForFlexCrf(false, path);
    }

    private static Path prepareForFlexCrf(boolean named, Path path) throws IOException, InterruptedException {
        prepareFiles();
        //prepare input folder
        Path input = Paths.get(path.toString() + "_folder", path.toFile().getName());
        //moves file to made path
        if (!Files.exists(input.getParent()))
            Files.createDirectories(input.getParent());
        Files.copy(path, input, StandardCopyOption.REPLACE_EXISTING);
        String pathAddress = input.toFile().getAbsolutePath();
        String outputPathAddress = pathAddress + ".out";
        //removes extra
        logger.trace("cutting first column.");
        Main.main(array("filemixer", "i1=" + pathAddress, "i2=" + pathAddress,
                "o=" + pathAddress + ".out", "c=0:0"));
        String TAGGED = pathAddress + ".pos";
        logger.trace("running pos tagger.");
        if (!Files.exists(input.resolve(TAGGED)) || Files.size(input.resolve(TAGGED)) == 0)
            NativeCommandRunner.runCommand("hunpos-tag",
                    FileHandler.getPath("pos", "model_98_accuracy").toString(),
                    "<", outputPathAddress, ">", TAGGED);
        logger.trace("pos tagger has been ended. prepare to fix its output.");
        String FIXED = pathAddress + ".fixed";
        Main.main(array("postagfixer", "i=" + TAGGED, "o=" + FIXED));
        logger.trace("going to transliterate");
        String TRANS = pathAddress + ".trans";
        Main.main(array("transliterator", "i=" + FIXED, "o=" + TRANS, "l=true", "m=true"));
        String FARSI_NORM = pathAddress + ".fnorm";
        Main.main(array("sentencemarker", "i=" + FIXED, "o=" + FARSI_NORM));
        String NORM = pathAddress + ".norm";
        Main.main(array("sentencemarker", "i=" + TRANS, "o=" + NORM));
        logger.trace("make flex crf file");
        String CRF_DATA = Paths.get(input.getParent().toString(), "data.untagged").toString();
        if (!Files.exists(input.resolve(CRF_DATA)) || Files.size(input.resolve(CRF_DATA)) == 0)
            if (named) {
                String NAMED = pathAddress + ".named";
                Main.main(array("pnamemarker", "i=" + NORM, "o=" + NAMED, "cc=2", "tc=1", "dc=0", "-t"));
                Main.main(array("NerFeatureGenerator", "-ulb", NAMED, CRF_DATA, "no"));
            } else
                Main.main(array("NerFeatureGenerator", "-ilb", NORM, CRF_DATA, "no"));
        return input;
    }

    public static Path namedEntityRecognize(boolean named, Path path) throws IOException, InterruptedException {
        Path input = prepareForFlexCrf(named, path);
        String PREDICTED = predictCrf(named, input.getParent());
        String FARSI_NORM = input.toFile().getAbsolutePath() + ".fnorm";
        String FARSI_MODEL = input.toFile().getAbsolutePath() + ".out";
        Main.main(array("filemixer", "i1=" + FARSI_NORM, "i2=" + PREDICTED, "o=" + FARSI_MODEL, "c=0:0;1:-1"));
        SimpleNamer.manipulateFile(Paths.get(FARSI_MODEL), "PERS");
        SimpleNamer.manipulateFile(Paths.get(FARSI_MODEL), "ORG");
        return input.getParent();
    }

    private synchronized static String predictCrf(boolean named, Path folderOfFile) throws IOException, InterruptedException {
        Path CRF_DATA = folderOfFile.resolve("data.untagged");
        Path MODEL_FOLDER = named ? Paths.get("resources", "named_ner")
                : Paths.get("resources", "normal_ner");
        Files.copy(CRF_DATA, MODEL_FOLDER.resolve("data.untagged"), StandardCopyOption.REPLACE_EXISTING);
        NativeCommandRunner.runCommand("crf", "-prd", "-d", MODEL_FOLDER.toFile().getAbsolutePath(), "-o", "option.txt");
        Files.move(MODEL_FOLDER.resolve("data.untagged.model"), folderOfFile.resolve("data.untagged.model"),
                StandardCopyOption.REPLACE_EXISTING);
        return folderOfFile.resolve("data.untagged.model").toString();
    }
}
