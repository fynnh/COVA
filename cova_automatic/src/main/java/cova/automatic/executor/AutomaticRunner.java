package cova.automatic.executor;

import brut.androlib.AndrolibException;
import brut.directory.DirectoryException;
import cova.automatic.activities.ActivityTraverser;
import cova.automatic.apk.ApkSignHelper;
import cova.automatic.apk.ApktoolMapper;
import cova.automatic.apk.aapt.AaptHelper;
import cova.automatic.data.AnalysisResult;
import cova.automatic.data.TestInput;
import cova.automatic.data.TestResult;
import cova.automatic.instrument.SootInstrumenter;
import cova.automatic.instrument.TargetStrings;
import cova.automatic.results.ConstraintInformation;
import cova.automatic.results.ConstraintInformationReporter;
import cova.runner.AndroidApkAnalyzer;
import cova.setup.config.Config;
import cova.setup.config.DefaultConfigForAndroid;
import cova.source.IdManager;
import cova.source.SourceInformation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.xmlpull.v1.XmlPullParserException;
import soot.jimple.infoflow.android.axml.AXmlNode;
import soot.jimple.infoflow.android.manifest.ProcessManifest;

public class AutomaticRunner {

  public static final String PRE_STRING = "COVA_CONSTRAINT_INFORMATION";
  public static final String PRE_INFO_STRING = "COVA_CALL_INFORMATION";

  private static Path apkFile;
  private static Path configDir;
  private static Path platformDir;
  private static Path sourceCodeDir;

  public static void parseArgs(String[] args) throws ParseException {
    Options options = new Options();
    // standard options
    options.addOption("h", "help", false, "Print this message.");
    options.addOption("p", "platform", true, "The location of the android platform jars.");
    options.addRequiredOption(
        "apk",
        "apkLocation",
        true,
        "The location of the APK file. It can be a single Apk file or a directory.");
    options.addOption(
        "config",
        true,
        "The path of config files specified for your application: at least one of Configuration_APIs.txt, IO_APIs.txt and UICallback_APIs.txt.");
    options.addOption(
        "output_html",
        true,
        "Print results in HTML files, this option should be followed by the java source code path of your application.");

    CommandLineParser parser = new DefaultParser();
    CommandLine cmd = parser.parse(options, args);
    apkFile = Paths.get(cmd.getOptionValue("apk"));
    configDir = Paths.get(cmd.getOptionValue("config"));
    platformDir = Paths.get(cmd.getOptionValue("platform"));
    if (cmd.hasOption("output_html")) {
      sourceCodeDir = Paths.get(cmd.getOptionValue("output_html"));
    }
  }

  public static AnalysisResult doAnalysisForArgs(String[] args)
      throws IOException, UnrecoverableKeyException, KeyStoreException, NoSuchAlgorithmException,
          CertificateException, AndrolibException, DirectoryException, XmlPullParserException,
          InterruptedException, ParseException {
    parseArgs(args);

    Path jarPath = platformDir.resolve("android-28/android.jar");

    Path tmpDir = Files.createTempDirectory("cova_automatic_");

    Path targetApk = tmpDir.resolve(apkFile.getFileName());
    Path signedApk = tmpDir.resolve(apkFile.getFileName() + "-signed.apk");
    Path alignedApk = tmpDir.resolve(apkFile.getFileName() + "-aligned.apk");
    return AutomaticRunner.doAnalysis(
        apkFile, platformDir, jarPath, targetApk, signedApk, alignedApk, configDir);
  }

  public static AnalysisResult doAnalysis(
      Path apkFile,
      Path platformDir,
      Path jarPath,
      Path targetApk,
      Path signedApk,
      Path alignedApk,
      Path configDir)
      throws IOException, XmlPullParserException, UnrecoverableKeyException, KeyStoreException,
          NoSuchAlgorithmException, CertificateException, InterruptedException, AndrolibException,
          DirectoryException {
    ApkSignHelper apkSignHelper = new ApkSignHelper();
    SootInstrumenter instrumenter = new SootInstrumenter();
    long instrumentStart = System.currentTimeMillis();

    // Instrument apk with logcat outputs
    List<TargetStrings> possibleTargets = instrumenter.instrument(apkFile, targetApk, platformDir);

    // sign instrumented apk
    apkSignHelper.sign(targetApk, signedApk, alignedApk);

    long instrumentEnd = System.currentTimeMillis();

    // uncomment to test without instrumentation
    // targetApk = apkFile;

    long preprocessStart = System.currentTimeMillis();

    // Get main activity from manifest
    ProcessManifest manifest = new ProcessManifest(targetApk.toFile());
    String mainActivity = null;
    for (AXmlNode activity : manifest.getActivities()) {
      for (AXmlNode c : activity.getChildren()) {
        for (AXmlNode c2 : c.getChildren()) {
          if ("android.intent.action.MAIN".equals(c2.getAttribute("name").getValue())) {
            mainActivity = (String) activity.getAttribute("name").getValue();
          }
        }
      }
    }

    // get id to string mappings of apk
    Map<String, Map<Integer, String>> baseMapping = ApktoolMapper.getMapping(apkFile);
    Map<Integer, String> mapping = baseMapping.get("mapping");
    Map<Integer, String> layoutMapping = baseMapping.get("layoutMapping");
    // Map<Integer, String> idMapping = baseMapping.get("idMapping");

    // Parse xml events and insert into cova
    AaptHelper aapt = new AaptHelper();
    Map<Integer, List<SourceInformation>> sourceInfos = aapt.listCallbacks(apkFile, layoutMapping);
    for (Entry<Integer, List<SourceInformation>> infoE : sourceInfos.entrySet()) {
      IdManager.getInstance().getXmlSources().put(infoE.getKey(), infoE.getValue());
    }

    long preprocessEnd = System.currentTimeMillis();

    long covaStart = System.currentTimeMillis();

    // Run cova
    Config config = new DefaultConfigForAndroid();
    config.setStringTaintCreationRuleOn(true);
    config.setConfigDir(configDir.toString());
    config.setWriteJimpleOutput(true);

    // Enable dynamic ids
    IdManager.getInstance().enable();

    String sourceCodeDirStr = null;
    if (sourceCodeDir != null) {
      sourceCodeDirStr = sourceCodeDir.toString();
    }
    AndroidApkAnalyzer.analyzeApk(
        jarPath.toString(), targetApk.toString(), sourceCodeDirStr, true, config);

    List<ConstraintInformation> constraints = ConstraintInformationReporter.getInformation(mapping);

    long covaEnd = System.currentTimeMillis();

    System.out.println("Traverse Acitivities");

    long activityTraverserStart = System.currentTimeMillis();

    for (ConstraintInformation cInfo : constraints) {
      ActivityTraverser traverser = new ActivityTraverser(constraints, cInfo, mainActivity);
      traverser.traverse();
      List<List<ConstraintInformation>> paths = traverser.getPaths();
      cInfo.setPaths(paths);
    }
    long activityTraverserEnd = System.currentTimeMillis();
    System.out.println("Finish traversing");

    AnalysisResult result = new AnalysisResult();
    result.setMainActivity(mainActivity);
    result.setConstraints(constraints);
    result.setApkPath(alignedApk);
    result.setInstrumentTimeInMillis(instrumentEnd - instrumentStart);
    result.setPreprocessTimeInMillis(preprocessEnd - preprocessStart);
    result.setCovaTimeInMillis(covaEnd - covaStart);
    result.setActivityTimeInMillis(activityTraverserEnd - activityTraverserStart);
    result.setPossibleTargets(possibleTargets);
    result.setMapping(mapping);
    return result;
  }

  public static TestResult testApp(TestInput input, Appium appium, Path recordingFile)
      throws IOException {
    if (input.getSelectedConstraint() == null) {
      throw new RuntimeException("No contraint selected yet");
    }
    // Get possible paths to target
    List<List<ConstraintInformation>> paths = input.getSelectedConstraint().getPaths();
    if (paths.isEmpty()) {
      System.err.println("No path to constraint");
      System.err.println(input.getSelectedConstraint().getMethod());
      System.err.println(input.getSelectedConstraint().getOutput());
      System.err.println(input.getSelectedConstraint().getConstraint());
      System.err.println(input.getSelectedConstraint().getConstraintMap());

      return null;
    }

    // Check if one path reaches destination
    TestResult result = null;
    for (List<ConstraintInformation> path : paths) {

      result =
          appium.executeApp(
              recordingFile, path, input.getSelectedConstraint().getOutput(), input.getMapping());
      if (result.isReachedDestination()) {
        break;
      }
    }
    return result;
  }
}