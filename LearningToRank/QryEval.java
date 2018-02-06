/*
 *  Copyright (c) 2017, Carnegie Mellon University.  All Rights Reserved.
 *  Version 3.3.1.
 */
import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.*;

import org.apache.lucene.analysis.Analyzer.TokenStreamComponents;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

/**
 *  This software illustrates the architecture for the portion of a
 *  search engine that evaluates queries.  It is a guide for class
 *  homework assignments, so it emphasizes simplicity over efficiency.
 *  It implements an unranked Boolean retrieval model, however it is
 *  easily extended to other retrieval models.  For more information,
 *  see the ReadMe.txt file.
 */
public class QryEval {

  //  --------------- Constants and variables ---------------------

  private static final String USAGE =
    "Usage:  java QryEval paramFile\n\n";

  private static final String[] TEXT_FIELDS =
    { "body", "title", "url", "inlink" };


  //  --------------- Methods ---------------------------------------

  /**
   *  @param args The only argument is the parameter file name.
   *  @throws Exception Error accessing the Lucene index.
   */
  public static void main(String[] args) throws Exception {

    //  This is a timer that you may find useful.  It is used here to
    //  time how long the entire program takes, but you can move it
    //  around to time specific parts of your code.
    
    Timer timer = new Timer();
    timer.start ();

    //  Check that a parameter file is included, and that the required
    //  parameters are present.  Just store the parameters.  They get
    //  processed later during initialization of different system
    //  components.

    if (args.length < 1) {
      throw new IllegalArgumentException (USAGE);
    }

    Map<String, String> parameters = readParameterFile (args[0]);

    Idx.open (parameters.get ("indexPath"));
    if (parameters.get("retrievalAlgorithm").toLowerCase().equals("letor")) {
        LearningToRank(parameters);
    } else {
    //  Open the index and initialize the retrieval model.

        
        RetrievalModel model = initializeRetrievalModel (parameters);

        //  Perform experiments.
        // if feedback(fb) is missing from the parameter file or fb is set to false, use
        // the original query to retrieve documents.
//        System.out.println("fb: " + parameters.get("fb"));
        if (!parameters.containsKey("fb") || parameters.get("fb").equals("false")) {
//            if (parameters.get ("retrievalAlgorithm").toLowerCase().equals("letor")) {
//              LearningToRank(parameters);
//            } else {
              processQueryFile(parameters.get("queryFilePath"), parameters.get("trecEvalOutputPath"), 
                    parameters.get("trecEvalOutputLength"), model);
//            }
        } else {
            // query expansion.
            String initialDocFile = null;
            int fbDocs = Integer.parseInt(parameters.get("fbDocs"));
            double fbOrigWeight = Double.parseDouble(parameters.get("fbOrigWeight"));
            List<Integer> queryID = new ArrayList<Integer>();
            List<ArrayList<DocScore>> initialDocs = new ArrayList<ArrayList<DocScore>>();
            if (parameters.containsKey("fbInitialRankingFile")) {
                initialDocFile = parameters.get("fbInitialRankingFile");
                initialDocs = getInitialDoc(initialDocFile, fbDocs, queryID);
            } else {
                initialDocs = getDefaultInitialDoc(parameters.get("queryFilePath"), fbDocs, queryID, model);
            }
//            System.out.println("size of initial documents: " + initialDocs.size());
            
            String fbExpansionQueryFile = parameters.get("fbExpansionQueryFile");
            int fbTerms = Integer.parseInt(parameters.get("fbTerms"));
            int fbMu = Integer.parseInt(parameters.get("fbMu"));
            ArrayList<String> expandedQuery = queryExpansion(fbExpansionQueryFile, fbTerms, fbMu, fbOrigWeight, initialDocs, queryID);
            processExpandedQuery(parameters.get("queryFilePath"), expandedQuery, parameters.get("trecEvalOutputPath"), 
                    parameters.get("trecEvalOutputLength"), model, fbOrigWeight);
        }
    }
    //  Clean up.
    
    timer.stop ();
    System.out.println ("Time:  " + timer);
  }
  
  static void LearningToRank(Map<String, String> parameters) throws Exception {
    String trainingQueryFile = parameters.get("letor:trainingQueryFile");
    String trainingQrelsFile = parameters.get("letor:trainingQrelsFile");
    String trainingFeaVecFile = parameters.get("letor:trainingFeatureVectorsFile");
    String disableFeatures = parameters.get("letor:featureDisable");
    String svmRankLearn = parameters.get("letor:svmRankLearnPath");
    String c = parameters.get("letor:svmRankParamC");
    String modelOutputFile = parameters.get("letor:svmRankModelFile");
    
    HashSet<Integer> featureDisable = new HashSet<Integer>();
    if (disableFeatures != null) {
        featureDisable = getDisableFeatures(disableFeatures);
    }
    
    HashMap<String, List<String>> relJudges = getrelJudges(trainingQrelsFile);
    processTrainingQueryFile(trainingQueryFile, relJudges, parameters, trainingFeaVecFile, featureDisable);
//     System.out.println("process training query file finished.");
    svmTrain(svmRankLearn, c, trainingFeaVecFile, modelOutputFile);
//     System.out.println("svm train finished.");
    
    String queryFilePath = parameters.get("queryFilePath");
    String testingFeaVecFile = parameters.get("letor:testingFeatureVectorsFile");
    String svmRankClassify = parameters.get("letor:svmRankClassifyPath");
    String testingDocScores = parameters.get("letor:testingDocumentScores");
    List<FeatureVector> normalizedFeatures = processQueryFile(queryFilePath, parameters, testingFeaVecFile, featureDisable);
//     System.out.println("process Query File finished.");
    svmClassify(svmRankClassify, testingFeaVecFile, modelOutputFile, testingDocScores);
//     System.out.println("svm classify finished.");
    
    String trecEvalOutputPath = parameters.get("trecEvalOutputPath");
    reRankDocs(testingDocScores, trecEvalOutputPath, normalizedFeatures);
  }

private static void reRankDocs(String testingDocScores, String trecEvalOutputPath, List<FeatureVector> features) throws Exception {
      FileReader reader = null;
      BufferedReader input = null;
//      FileWriter writer = null;
      try {
          reader = new FileReader(testingDocScores);
          input = new BufferedReader(reader);
//          writer = new FileWriter(new File(trecEvalOutputPath), true);
          List<String> queryIds = new ArrayList<String>();
          List<ArrayList<DocScore>> docScores = new ArrayList<ArrayList<DocScore>>();
          ArrayList<DocScore> specDocScores = null;
          String line = null;
          String prev = null;
          int index = 0;
          // read scores from svm classification file
//           System.out.println("size of feature: " + features.size());
          while ((line = input.readLine()) != null && index < features.size()) {
              double score = Double.parseDouble(line);
              FeatureVector vector = features.get(index);
              String qid = vector.qid;
              if (prev == null || !prev.equals(qid)) {
                  queryIds.add(qid);
                  specDocScores = new ArrayList<DocScore>();
                  docScores.add(specDocScores);
                  prev = qid;
              }
              String externalDocId = vector.docId;
//              System.out.println("reRankDocs:: external doc id is: " + externalDocId);
              int internalDocId = Idx.getInternalDocid(externalDocId);
              specDocScores.add(new DocScore(internalDocId, score));
              index++;
          }
          
          // sort
//           System.out.println("length: " + docScores.size());
          for (int i = 0; i < docScores.size(); i++) {
              Collections.sort(docScores.get(i), new DocScore());
              printResults(queryIds.get(i), docScores.get(i), trecEvalOutputPath);
          }
      } catch(Exception e) {
          e.printStackTrace();
      } finally {
          reader.close();
          input.close();
          
      }
  }
  
  static void printResults(String queryName, List<DocScore> docScores, String trecEvalOutputPath) throws IOException {
//  static void printResults(String queryName, ScoreList result) throws IOException {

      File file = new File(trecEvalOutputPath);
      FileWriter writer = new FileWriter(file, true);
      if (docScores.size() < 1) {
          String s = queryName + "  Q0  dummy  1  0  fubar\n";
          System.out.println(s);
          writer.write(s);
          writer.flush();
      } else {
          for (int i = 0; i < docScores.size(); i++) {
              String score = String.format("%.18f", docScores.get(i).getDocScore());
              String externalDocId = Idx.getExternalDocid(docScores.get(i).getDocID());
              String s = queryName + "  Q0  " + externalDocId + "  "  + (i + 1) + "  " + score + "  fubar\n";
              System.out.println(s);
              writer.write(s);
              writer.flush();
          }
      }
      writer.close();
  }
  
  /**
   * method to get disabled features.
   * @param disableFeatures disabled features given by the parameter file
   * @return HashSet containing all the disabled features.
   */
  private static HashSet<Integer> getDisableFeatures(String disableFeatures) {
      HashSet<Integer> disable = new HashSet<Integer>();
      String[] strings = disableFeatures.split(",");
      for (int i = 0; i < strings.length; i++) {
          disable.add(Integer.parseInt(strings[i]));
      }
      return disable;
  }
  
  private static void svmClassify(String execPath, String testingFeaVecFile, String modelOutputFile,
          String testingDocScores) throws IOException, Exception {
      // runs svm_rank_learn from within Java to train the model
      // execPath is the location of the svm_rank_learn utility,
      // which is specified by letor:svmRankLearnPath in the parameter file.
      // FEAT_GEN.c is the value of the letor:c parameter.
      Process cmdProc = Runtime.getRuntime().exec(
              new String[] {execPath, testingFeaVecFile, modelOutputFile, testingDocScores});

      // The stdout/stderr consuming code MUST be included.
      // It prevents the OS from running out of output buffer space and stalling.

      // consume stdout and print it out for debugging purposes
      BufferedReader stdoutReader = new BufferedReader(
              new InputStreamReader(cmdProc.getInputStream()));
      String line;
      while ((line = stdoutReader.readLine()) != null) {
        System.out.println(line);
      }
      // consume stderr and print it for debugging purposes
      BufferedReader stderrReader = new BufferedReader(
              new InputStreamReader(cmdProc.getErrorStream()));
      while ((line = stderrReader.readLine()) != null) {
        System.out.println(line);
      }

      // get the return value from the executable. 0 means success, non-zero
      // indicates a problem
      int retValue = cmdProc.waitFor();
      if (retValue != 0) {
        throw new Exception("SVM Rank crashed.");
      }
  }
  
  private static void svmTrain(String execPath, String c, String qrelsFeatureOutputFile, 
          String modelOutputFile) throws Exception {
   // runs svm_rank_learn from within Java to train the model
      // execPath is the location of the svm_rank_learn utility, 
      // which is specified by letor:svmRankLearnPath in the parameter file.
      // FEAT_GEN.c is the value of the letor:c parameter.
      Process cmdProc = Runtime.getRuntime().exec(
          new String[] { execPath, "-c", String.valueOf(c), qrelsFeatureOutputFile,
              modelOutputFile });

      // The stdout/stderr consuming code MUST be included.
      // It prevents the OS from running out of output buffer space and stalling.

      // consume stdout and print it out for debugging purposes
      BufferedReader stdoutReader = new BufferedReader(
          new InputStreamReader(cmdProc.getInputStream()));
      String line;
      while ((line = stdoutReader.readLine()) != null) {
        System.out.println(line);
      }
      // consume stderr and print it for debugging purposes
      BufferedReader stderrReader = new BufferedReader(
          new InputStreamReader(cmdProc.getErrorStream()));
      while ((line = stderrReader.readLine()) != null) {
        System.out.println(line);
      }

      // get the return value from the executable. 0 means success, non-zero 
      // indicates a problem
      int retValue = cmdProc.waitFor();
      if (retValue != 0) {
        throw new Exception("SVM Rank crashed.");
      }
  }
  
  private static void processTrainingQueryFile(String trainingQueryFile, HashMap<String, List<String>> relJudges, 
          Map<String, String> parameters, String trainingFeaVecFile, HashSet<Integer> disableFeatures) throws Exception {
      BufferedReader input = null;
      FileReader reader = null;
      List<FeatureVector> featureList = new ArrayList<FeatureVector>();
      try {
          // read from the training query file
          reader = new FileReader(trainingQueryFile);
          input = new BufferedReader(reader);
          String trainQueryLine = null;
          
          // while a training query q is available
          while((trainQueryLine = input.readLine()) != null) {
               int index = trainQueryLine.indexOf(':');
               if (index < 0) {
                   throw new IllegalArgumentException("Syntax error: missing ':' in query line.\n");
               }
               String qid = trainQueryLine.substring(0, index);
               String query = trainQueryLine.substring(index + 1);
               System.out.println("qid: " + qid + "  query: " + query);
               
               // tokenize query to stop and stem the query.
               String[] queryTerms = QryParser.tokenizeString(query);
               System.out.println("After tokenizing the query --->" + Arrays.toString(queryTerms));
               
               List<FeatureVector> originalFeatures = new ArrayList<FeatureVector>();
               // for each document d in the relevance judgments for training query q
               FeatureVector features;
               
               List<String> docs = relJudges.getOrDefault(qid, null);
               if (docs == null) {
                   throw new IllegalArgumentException
                   ("Error: no corresponding relevance judgments for query " + qid);
               } else {
                   for (int i = 0; i < docs.size(); i++) {
                       String judge = docs.get(i);
                       String[] columns = judge.split(" +");
//                       System.out.println("relevance judgments columns: " + Arrays.toString(columns));
                       
                       features = calculateScores(columns, parameters, queryTerms, qid);
                       originalFeatures.add(features);
                   }
                   
                   List<FeatureVector> feature = normalizeFeatures(originalFeatures, trainingFeaVecFile, disableFeatures);
                   featureList.addAll(feature);
               }
          }
      } catch(IOException e) {
          e.printStackTrace();
      } finally {
          reader.close();
          input.close();
      }
//      return featureList;
  }

private static List<FeatureVector> normalizeFeatures(List<FeatureVector> originalFeatures, 
        String trainingFeaVecFile, HashSet<Integer> disableFeatures) throws IOException {
    FileWriter writer = null;
    List<FeatureVector> normalizedFeatures = null;
    try {
        double[] minValues = new double[18];
        Arrays.fill(minValues, Double.MAX_VALUE);
        double[] maxValues = new double[18];
        Arrays.fill(maxValues, Double.MIN_VALUE);
        // find the maximum and minimum values for each feature for a specific query
        // the passed original feature list contains only feature vectors for one specified query.
        for (int i = 0; i < originalFeatures.size(); i++) {
            double[] features = originalFeatures.get(i).featureVector;
            for (int j = 0; j < features.length; j++) {
//                System.out.println("feature " + j + ": " + features[j]);
                if (Double.compare(features[j], Double.NaN) != 0) {
                    minValues[j] = Math.min(minValues[j], features[j]);
                    maxValues[j] = Math.max(maxValues[j], features[j]);
                }
            }
        }
//        System.out.println("min values: " + Arrays.toString(minValues));
//        System.out.println("max values: " + Arrays.toString(maxValues));
        normalizedFeatures = new ArrayList<FeatureVector>();
        File file = new File(trainingFeaVecFile);
        writer = new FileWriter(file, true);
        for (int i = 0; i < originalFeatures.size(); i++) {
            FeatureVector feaVec = originalFeatures.get(i);
            double[] features = feaVec.featureVector;
            FeatureVector vector = new FeatureVector();
            vector.docId = feaVec.docId;
            vector.qid = feaVec.qid;
            vector.relevance = feaVec.relevance;
            String qid = feaVec.qid;
            String externalDocId = feaVec.docId;
            String relevance = feaVec.relevance;
            StringBuilder sb = new StringBuilder();
            sb.append(relevance);
            sb.append(" qid:");
            sb.append(qid);
            sb.append(" ");
            
            for (int j = 0; j < features.length; j++) {
                if (disableFeatures.contains(j + 1)) {
                    continue;
                }
                double normalized;
                
                if (Double.compare(features[j], Double.NaN) == 0 || maxValues[j] == minValues[j]) {
                    normalized = 0.0;
                } else {
                    normalized = (features[j] - minValues[j]) / (maxValues[j] - minValues[j]);
                }
                vector.setFeature(j + 1, normalized);
                // if (j == 5) {
//                     System.out.println("Max: " + maxValues[j] + "  Min: " + minValues[j] 
//                             + " Normalized: " + normalized + " Original: " + features[j]);
//                 }
               
                sb.append(j + 1);
                sb.append(":");
                sb.append(normalized);
                sb.append(" ");
            }

            sb.append("# ");
            sb.append(externalDocId);       // external document id
            sb.append("\n");
            writer.write(sb.toString());
            normalizedFeatures.add(vector);
            
        }
    } catch (IOException e) {
        e.printStackTrace();
    } finally {
        writer.close();
    }
    return normalizedFeatures;
}

/**
 * method to calculate scores for different features
 * @param columns  relevance judgment for the document d retrieved from the query q.
 * @param parameters   input parameters
 * @param queryTerms   query terms after tokenizing the query
 * @return  feature vector for the document
 * @throws Exception error when accessing term vector
 */
private static FeatureVector calculateScores(String[] columns, Map<String, String> parameters, 
        String[] queryTerms, String qid) throws Exception {
    FeatureVector features = new FeatureVector();
    String exId = columns[1];
    String relevance = columns[2];
    int internalId = Idx.getInternalDocid(exId);
    features.docId = exId;
    features.qid = qid;
    features.relevance = relevance;
    
    // calculate score for feature 1.
    double spam = Double.parseDouble(Idx.getAttribute("spamScore", internalId));                  
    features.setFeature(1, spam);
//    System.out.println("f1: " + spam);
    
    // calculate score for feature 2.
    String rawUrl = Idx.getAttribute("rawUrl", internalId);                                    
//    System.out.println("raw url: " + rawUrl + "  external document id: " + exId);
    int count = 0;
    for (int i = 0; i < rawUrl.length(); i++) {
        if (rawUrl.charAt(i) == '/') {
            count++;
        }
    }
    features.setFeature(2, count);
//    System.out.println("f2: " + count);
    
    // calculate score for feature 3
    boolean wiki = rawUrl.contains("wikipedia.org");                                             
    if (wiki) {
        features.setFeature(3, 1.0);
    } else {
        features.setFeature(3, 0.0);
    }
//    System.out.println("f3: " + wiki);
    
    // calculate score for feature 4
    double pageRank = Double.parseDouble(Idx.getAttribute("PageRank", internalId));               
    features.setFeature(4, pageRank);
//    System.out.println("f4: " + pageRank);
    
    TermVector bodyTermVec = new TermVector(internalId, "body");
    TermVector titleTermVec = new TermVector(internalId, "title");
    TermVector urlTermVec = new TermVector(internalId, "url");
    TermVector inlinkTermVec = new TermVector(internalId, "inlink");
    
    // get query term frequency
    Map<String, Integer> qtf = new HashMap<String, Integer>();
    for (int i = 0; i < queryTerms.length; i++) {
        qtf.put(queryTerms[i], qtf.getOrDefault(queryTerms[i], 0) + 1);
    }
//    System.out.println("query length: " + queryTerms.length);
   
    // calculate overlap scores of different fields first, cause this will be used when calculating
    // Indri score for different fields(If there is no terms matching the query in the document, then
    // this document should have score 0).
    features.setFeature(7, calculateOverlap(queryTerms, bodyTermVec));                           // f7
    features.setFeature(10, calculateOverlap(queryTerms, titleTermVec));                         // f10
    features.setFeature(13, calculateOverlap(queryTerms, urlTermVec));                           // f13
    features.setFeature(16, calculateOverlap(queryTerms, inlinkTermVec));                        // f16
    
    // calculate BM25 scores for different fields.
    double k1 = Double.parseDouble(parameters.get("BM25:k_1"));
    double b = Double.parseDouble(parameters.get("BM25:b"));
    double k3 = Double.parseDouble(parameters.get("BM25:k_3"));

    features.setFeature(5, calculateBM25(k1, b, k3, bodyTermVec, qtf, "body"));                  // f5
    features.setFeature(8, calculateBM25(k1, b, k3, titleTermVec, qtf, "title"));                // f8
    features.setFeature(11, calculateBM25(k1, b, k3, urlTermVec, qtf, "url"));                   // f11
    features.setFeature(14, calculateBM25(k1, b, k3, inlinkTermVec, qtf, "inlink"));             // f14
    
    // calculate Indri score for different field. If there is no term matching the query in that document
    // then give the document score 0.
    double mu = Double.parseDouble(parameters.get("Indri:mu"));
    double lambda = Double.parseDouble(parameters.get("Indri:lambda"));
    
    if (Double.compare(features.getFeature(7), 0.0) == 0) {                                                        // f6
        features.setFeature(6, 0.0);
    } else  {
//        double score = calculateIndri(lambda, mu, queryTerms, bodyTermVec, "body");
//        System.out.println("Func: score:  " + score);
        features.setFeature(6, calculateIndri(lambda, mu, queryTerms, bodyTermVec, "body"));         
    }

    if (features.getFeature(10) == 0.0) {                                                        // f9
        features.setFeature(9, 0.0);
    } else  {
        features.setFeature(9, calculateIndri(lambda, mu, queryTerms, titleTermVec, "title"));     
    }
    
    if (features.getFeature(13) == 0.0) {                                                        // f12
        features.setFeature(12, 0.0);
    } else  {
        features.setFeature(12, calculateIndri(lambda, mu, queryTerms, urlTermVec, "url"));    
    }

    if (features.getFeature(16) == 0.0) {                                                        // f15
        features.setFeature(15, 0.0);
    } else  {
        features.setFeature(15, calculateIndri(lambda, mu, queryTerms, inlinkTermVec, "inlink")); 
    }
    
    // custom features.
    features.setFeature(17, firstCustom(bodyTermVec));                                           // f17
    features.setFeature(18, secondCustom(queryTerms, bodyTermVec));                              // f18
    
    return features;
}

// the fraction of stopwords
private static double firstCustom(TermVector vector) {
    if (vector.stemsLength() == 0) return 0.0;          
    double score = 0.0;
    double fieldLen = vector.positionsLength();
    double words = 0.0;
    for (int i = 1; i < vector.stemsLength(); i++) {
        words += vector.stemAt(i);
    }
    double stopWords = fieldLen - words;
    score = stopWords / fieldLen;
//    System.out.println("first custom feature: " + score);
    return score;
}

// Ranked Boolean AND operator
private static double secondCustom(String[] queryTerms, TermVector vector) {
    if (vector.stemsLength() == 0) return Double.NaN;              
    double score = Double.MAX_VALUE;
    for (int i = 0; i < queryTerms.length; i++) {
        String term = queryTerms[i];
        int index = vector.indexOfStem(term);
        if (index != -1) {
            score = Math.min(score, vector.stemFreq(index));
        }
    }
    if (score == Double.MAX_VALUE) {
        score = 0.0;
    }
//    System.out.println("second custom feature: " + score);
    return score;
}

private static double calculateOverlap(String[] queryTerms, TermVector vector) throws IOException {
//    String externalDocId = Idx.getExternalDocid(vector.docId);
//    if (externalDocId.equals("clueweb09-en0001-18-32681")) {
//        System.out.println("clueweb09-en0001-18-32681: " + vector.stemsLength());
//    }
    if (vector.stemsLength() == 0) {
//        System.out.println(externalDocId + " get 0");
        return Double.NaN;  
    }
    double score = 0.0;
    int count = 0;
    for (int i = 0; i < queryTerms.length; i++) {
        String queryTerm = queryTerms[i];
        int index = vector.indexOfStem(queryTerm);
        if (index != -1) {
            count++;
        }
    }
    
    score = (double)count / (double)queryTerms.length;
//    System.out.println("overlap:: field: " + vector.fieldName + "  score: " + score);
    return score;
}

private static double calculateBM25(double k1, double b, double k3, TermVector vector, Map<String, Integer> qtf,
        String field) throws IOException {
    if (vector.stemsLength() == 0) {
        return Double.NaN;                 
    }
    double score = 0.0;
    double N = Idx.getNumDocs();
    double fieldLen = vector.positionsLength();
    double avgLen = (double)Idx.getSumOfFieldLengths(field) / (double)Idx.getDocCount(field);
    
    for (Map.Entry<String, Integer> entry : qtf.entrySet()) {
        String queryTerm = entry.getKey();
        int queryTermFreq = entry.getValue();
//        System.out.println(queryTerm);
        
        int index = vector.indexOfStem(queryTerm);
//        System.out.println("BM25:: index: " + index);
        if (index == -1) {
            continue;
        } else {
            double df = vector.stemDf(index);
            double tf = vector.stemFreq(index);
            double RSJWeight = Math.max(0, Math.log((N - df + 0.5) / (df +0.5)));
            double tfWeight = tf / (tf + k1 * (1 - b + b * fieldLen / avgLen));
            double userWeight = ((k3 + 1) * queryTermFreq / (k3 + queryTermFreq));
            score += RSJWeight * tfWeight * userWeight;
        }
    }
//    System.out.println("BM25:: field: " + field + "  score: " + score);       
    return score;
}

private static double calculateIndri(double lambda, double mu, String[] queryTerms, TermVector vector,
        String field) throws IOException {
    if (vector.stemsLength() == 0) return Double.NaN;
    double score = 1.0;
    double docLen = vector.positionsLength();
    double collecLen = (double)Idx.getSumOfFieldLengths(field);
    
    for (int i = 0; i < queryTerms.length; i++) {
        String term = queryTerms[i];
        int index = vector.indexOfStem(term);
        double tf;
        double ctf;
        if (index == -1) {
            tf = 0.0;
            ctf = Idx.getTotalTermFreq(field, term);
//            if (field.equals("body")){
//                System.out.println("Not exist: " + term + ", " + ctf);
//            }
           
        } else {
            tf = vector.stemFreq(index);
            ctf = vector.totalStemFreq(index);
//            if (field.equals("body")){
//                System.out.println("Exist: " + term + ", " + ctf);
//            }
        }
        
        double pMLEc = ctf / collecLen;
        double bayesSmooth = (tf + mu * pMLEc) / (docLen + mu);
        double tmp = (1 - lambda) * bayesSmooth + lambda * pMLEc;
        score *= tmp;
//        System.out.println(term + ", " + tf + ", " + ctf + ", " + docLen + ", " + collecLen + ", " + pMLEc + ", " + score);
        // if (Idx.getExternalDocid(vector.docId).equals("clueweb09-en0001-18-32681")) {
//             System.out.println("score: " +  score);
//         }
//        System.out.println("score: " +  score);
    }
    score = Math.pow(score, 1.0 / (double)queryTerms.length);
    // if (Idx.getExternalDocid(vector.docId).equals("clueweb09-en0001-18-32681")) {
//         System.out.println("Indri:: field: " + field + "   ExternalDocId: " + Idx.getExternalDocid(vector.docId) + "  score: " + score);
//     }
//    System.out.println("Indri:: field: " + field + "   ExternalDocId: " + Idx.getExternalDocid(vector.docId) + "  score: " + score);
    return score;
}

/**
 * Read from the training relevance judgments file. Group all the relevance judgments for the same
 * query and store it as <queryId, list of relevance judgments> pair
 * @param trainQrelsFile training relevance judgments file name
 * @return  HashMap containing lists of relevance judgments for different queries
 * @throws IOException error reading from training relevance judgments file
 */
private static HashMap<String, List<String>> getrelJudges(String trainQrelsFile) throws IOException {
      BufferedReader input = null;
      HashMap<String, List<String>> relJudges = new HashMap<String, List<String>>();
//      List<String> relJudges = new ArrayList<String>();
      try {
          FileReader reader = new FileReader(trainQrelsFile);
          input = new BufferedReader(reader);
          
          String doc = null;
//          String prev = null;
//          List<String> docs = null;
          while ((doc = input.readLine()) != null) {
              
              int index = doc.indexOf(' ');
              String qid = doc.substring(0, index);
              String docInfo = doc.substring(index + 1);
//              System.out.println(qid + " " + docInfo);
//              String[] columns = doc.split(" +");
//              String qid = columns[0];
//              if (prev == null || !prev.equals(qid)) {
//                  docs = new ArrayList<String>();
//                  relJudges.put(qid, new ArrayList<String>());
//              }
              List<String> docs = relJudges.getOrDefault(qid, new ArrayList<String>());
              docs.add(docInfo);
              relJudges.put(qid, docs);
          }
      } catch (IOException e) {
          e.printStackTrace();
      } finally {
          input.close();
      }
      return relJudges;
  }

  private static List<FeatureVector> processQueryFile(String queryFilePath, Map<String, String> parameters,
          String testingFeaVecFile, HashSet<Integer> disableFeatures) throws Exception {
      BufferedReader input = null;
      HashMap<String, List<DocScore>> initialRank = new HashMap<String, List<DocScore>>();

      double k1 = Double.parseDouble(parameters.get("BM25:k_1"));
      double b = Double.parseDouble(parameters.get("BM25:b"));
      double k3 = Double.parseDouble(parameters.get("BM25:k_3"));
      RetrievalModel model = new RetrievalModelBM25(k1, k3, b);
//      List<String> exDocs = new ArrayList<String>();
      List<FeatureVector> normalizedFeatures = null;
      List<FeatureVector> originalFeatures = null;
      List<FeatureVector> finalFeatures = new ArrayList<FeatureVector>();
//      List<DocScore> initialRank = new ArrayList<DocScore>();
      try {
          String qLine = null;
          input = new BufferedReader(new FileReader(queryFilePath));
          // for each query
          while((qLine = input.readLine()) != null) {


              int d = qLine.indexOf(':');

              if (d < 0) {
                throw new IllegalArgumentException
                  ("Syntax error:  Missing ':' in query line.");
              }

              printMemoryUsage(false);

              String qid = qLine.substring(0, d);
              String query = qLine.substring(d + 1);

              System.out.println("Query " + qLine);

              ScoreList r = null;

      //        System.out.println("query: " + query);
              r = processQuery(query, model);

              List<DocScore> docScore = new ArrayList<DocScore>();
              for (int i = 0; i < Math.min(100, r.size()); i++) {
                  docScore.add(new DocScore(r.getDocid(i), r.getDocidScore(i)));
              }
              
              String[] queryTerms = QryParser.tokenizeString(query);
              System.out.println("After tokenizing the query: " + Arrays.toString(queryTerms));
              
              // calculate query term frequency
              HashMap<String, Integer> qtf = new HashMap<String, Integer>();
              for (int i = 0; i < queryTerms.length; i++) {
                  String term = queryTerms[i];
                  qtf.put(term, qtf.getOrDefault(term, 0) + 1);
              }

              initialRank.put(qid, docScore);
              
              originalFeatures = new ArrayList<FeatureVector>();
//              List<String> externalDocs = new ArrayList<String>(); 
              for (int i = 0; i < docScore.size(); i++) {
                  DocScore doc = docScore.get(i);
                  int internalDocId = doc.getDocID();
                  String externalId = Idx.getExternalDocid(internalDocId);
                  double score = doc.getDocScore();
//                  System.out.println("docId: " + externalId + " score: " + score);
                  String[] columns = new String[3];
                  columns[1] = externalId;
                  columns[2] = new String("0");
                  FeatureVector features = calculateScores(columns, parameters, queryTerms, qid);
                  features.docId = externalId;
                  features.qid = qid;
                  features.relevance = new String("0");
                  originalFeatures.add(features);
              }
              normalizedFeatures = normalizeFeatures(originalFeatures, testingFeaVecFile, disableFeatures);
              finalFeatures.addAll(normalizedFeatures);
//              exDocs.addAll(externalDocs);
          }
      } catch (Exception e) {
          e.printStackTrace();
      } finally {
          input.close();
      }
      return finalFeatures;
  }

  static ArrayList<String> queryExpansion(String fbExpansionQueryFile, int fbTerms, int fbMu, double fbOrigWeight,
          List<ArrayList<DocScore>> initialDocFile, List<Integer> queryID) throws IOException {
      ArrayList<String> expandedQuery = new ArrayList<String>();
      HashMap<String, Double> termScoreMap = new HashMap<String, Double>();     // used to update score for each term
      HashMap<String, Long> termCtf = new HashMap<String, Long>();              // store ctf for each term
      ArrayList<TermVector> termVecList = new ArrayList<TermVector>();          // store TermVectors
      long collecLength = Idx.getSumOfFieldLengths("body");
      PriorityQueue<TermScore> expandedTerms = new PriorityQueue<TermScore>(fbTerms, new TermScore());
      
      // for each query, calculate score for candidate expansion terms.
      for (int i = 0; i < initialDocFile.size(); i++) {
          ArrayList<DocScore> doc = initialDocFile.get(i);
//          System.out.println("Doc list: " + Arrays.toString(doc.toArray()));
          termVecList = new ArrayList<TermVector>();
          termScoreMap = new HashMap<String, Double>();
          
          for (int index = 0; index < doc.size(); index++) {
              int docID = doc.get(index).getDocID();
              double origScore = doc.get(index).getDocScore();
//              System.out.println("docID: " + docID + "   score:" + origScore);
              TermVector termVec = new TermVector(docID, "body");
              termVecList.add(termVec);
              
              // calculate score for each term in forward list
              for (int j = 1; j < termVec.stemsLength(); j++) {     // 0 is stop word
                  String term = termVec.stemString(j);
                  // ignore candidate terms containing a period or a comma
                  if (term.indexOf(',') >= 0 || term.indexOf('.') >= 0) {
                      continue;
                  }
                  
                  int tf = termVec.stemFreq(j);
                  long ctf = termVec.totalStemFreq(j);
                  if (!termCtf.containsKey(term)) {
                      termCtf.put(term, ctf);
                  }
                  int docLength = termVec.positionsLength();
//                  System.out.println(term + " " + origScore + " " + tf + " " + ctf + " " + collecLength + " " + docLength);
                  double ptd = ((double)tf + (double)fbMu * (double)ctf / (double)collecLength) / ((double)docLength + (double)fbMu);
//                  System.out.println("queryExpansion:: ptd: " + ptd);
                  double score = ptd * origScore * Math.log(collecLength / ctf);
//                  System.out.println("queryExpansion:: single score: " + score);

                  termScoreMap.put(term, termScoreMap.getOrDefault(term, 0.0) + score);
//                  System.out.println("queryExpansion:: single term: " + term + " score: " + termScoreMap.get(term));
              }
          }

          // calculate final score for each candidate expansion term. If the term is not in the current document,
          // it still needs to do smoothing.
          expandedTerms = new PriorityQueue<TermScore>(fbTerms, new TermScore());
          Set<Map.Entry<String, Double>> entries = termScoreMap.entrySet();
          Iterator<Map.Entry<String, Double>> iterator = entries.iterator();
          while(iterator.hasNext()) {
              Map.Entry<String, Double> entry = iterator.next();
              String term = entry.getKey();
//              Double score = entry.getValue();
              Double tmpScore = 0.0;
              long ctf = termCtf.get(term);
//              System.out.println("size of termVector list: " + termVecList.size() + "  size of doc: " + doc.size());
              for (int j = 0; j < termVecList.size(); j++) {
//                  System.out.println("j: " + j + "  size of doc: " + doc.size());
                  TermVector termVec = termVecList.get(j);
                  if (termVec.indexOfStem(term) == -1) {
                      double ptd = ((double)fbMu * (double)ctf / (double)collecLength) / ((double)termVec.positionsLength() + (double)fbMu);
//                    System.out.println("fbMu: " + fbMu + " " + ctf / collecLength + " " + termVec.positionsLength() + " ptd: " + ptd);
                      double original = doc.get(j).getDocScore();
//                    System.out.println("original: " + original);
                      tmpScore = ptd * original * Math.log(collecLength / ctf);
//                    System.out.println("score: " + score);
                      termScoreMap.put(term, termScoreMap.get(term) + tmpScore);
//                    System.out.println("after modifying: " + termScoreMap.get(term));
                  }
              }
              expandedTerms.add(new TermScore(term, termScoreMap.get(term)));
          }
//          System.out.println("size of expandedTerms: " + expandedTerms.size());
          // Catch the top fbTerms terms and complete query expansion.
          StringBuilder sb = new StringBuilder();
          sb.append("#wand (");
          DecimalFormat decimalFormat = new DecimalFormat("#0.0000");
          for (int j = 0; j < fbTerms; j++) {
              TermScore termScore = expandedTerms.poll();
//              System.out.println("term: " + termScore.getTerm() + "   score: " + termScore.getScore());
              sb.append(" ");
              sb.append(decimalFormat.format(termScore.getScore()));
              sb.append(" ");
              sb.append(termScore.getTerm());
          }
          sb.append(")");
//          System.out.println("queryExpansion:: expanded query using expanded candidate terms: " + sb.toString());
          expandedQuery.add(sb.toString());
//           System.out.println();
      }
      
      // Write query to file
      File file = new File(fbExpansionQueryFile);
      FileWriter writer = new FileWriter(file, true);
      for (int i = 0 ; i < expandedQuery.size(); i++) {
          writer.write(queryID.get(i) + ": " + expandedQuery.get(i) + "\n");
          writer.flush();
      }
      writer.close();
      
      return expandedQuery;
  }

  /**
   *  Allocate the retrieval model and initialize it using parameters
   *  from the parameter file.
   *  @return The initialized retrieval model
 * @throws Exception 
   */
  private static RetrievalModel initializeRetrievalModel (Map<String, String> parameters)
    throws Exception {

    RetrievalModel model = null;
    String modelString = parameters.get ("retrievalAlgorithm").toLowerCase();

    if (modelString.equals("unrankedboolean")) {
      model = new RetrievalModelUnrankedBoolean();
//    }
    } else if (modelString.equals("rankedboolean")) {
        model = new RetrievalModelRankedBoolean();
    } else if (modelString.equals("bm25")) {
        double k1 = Double.parseDouble(parameters.get("BM25:k_1"));
        double k3 = Double.parseDouble(parameters.get("BM25:k_3"));
        double b = Double.parseDouble(parameters.get("BM25:b"));
//        System.out.println("k1: " + k1 + " k3: " + k3 + " b: " + b);
        model = new RetrievalModelBM25(k1, k3, b);
    } else if (modelString.equals("indri")) {
//        System.out.println("QryEval initializeRetrievalModel");
        double mu = Double.parseDouble(parameters.get("Indri:mu"));
        double lambda = Double.parseDouble(parameters.get("Indri:lambda"));
        model = new RetrievalModelIndri(mu, lambda);
    } else if (modelString.equals("letor")) {
        LearningToRank(parameters);
    }
    else {
      throw new IllegalArgumentException
        ("Unknown retrieval model " + parameters.get("retrievalAlgorithm"));
    }
      
    return model;
  }

  /**
   * Print a message indicating the amount of memory used. The caller can
   * indicate whether garbage collection should be performed, which slows the
   * program but reduces memory usage.
   * 
   * @param gc
   *          If true, run the garbage collector before reporting.
   */
  public static void printMemoryUsage(boolean gc) {

    Runtime runtime = Runtime.getRuntime();

    if (gc)
      runtime.gc();

    System.out.println("Memory used:  "
        + ((runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)) + " MB");
  }

  /**
   * Process one query.
   * @param qString A string that contains a query.
   * @param model The retrieval model determines how matching and scoring is done.
   * @return Search results
   * @throws IOException Error accessing the index
   */
  static ScoreList processQuery(String qString, RetrievalModel model)
    throws IOException {
//      System.out.println("processQuery:: query: " + qString);

    String defaultOp = model.defaultQrySopName ();
    qString = defaultOp + "(" + qString + ")";
    Qry q = QryParser.getQuery (qString);

    // Show the query that is evaluated
    
    System.out.println("    --> " + q);
    
    if (q != null) {

      ScoreList r = new ScoreList ();
      
      if (q.args.size () > 0) {		// Ignore empty queries

        q.initialize (model);

//        System.out.println("QryIval processQuery");
        while (q.docIteratorHasMatch (model)) {
          int docid = q.docIteratorGetMatch ();   // QryIop
//          System.out.println("in QryEval class docIteratorHasMatch   " + docid);
          double score = ((QrySop) q).getScore (model);   // QrySop
          // if (Idx.getExternalDocid(docid).equals("clueweb09-en0000-01-21462")) {
//               System.out.println(model.defaultQrySopName() + ", " + score);
//           }
          r.add (docid, score);
          q.docIteratorAdvancePast (docid);
        }
      }
      // changed. add sort method.
      r.sort();

      return r;
    } else
      return null;
  }

  static List<ArrayList<DocScore>> getDefaultInitialDoc(String queryFilePath, int fbDocs, List<Integer> queries, RetrievalModel model) throws IOException {
      List<ArrayList<DocScore>> initialDocs = new ArrayList<ArrayList<DocScore>>();
      BufferedReader input = null;
      try {
          String qLine = null;
          input = new BufferedReader(new FileReader(queryFilePath));
          while ((qLine = input.readLine()) != null) {
              ArrayList<DocScore> docs = new ArrayList<DocScore>();
              int d = qLine.indexOf(":");
              if (d < 0) {
                  throw new IllegalArgumentException("Syntax error: Missing ':' in query line.");
              }
              int qid = Integer.parseInt(qLine.substring(0, d));
              String query = qLine.substring(d + 1);
//              System.out.println("getDefaultInitialDoc:: query ID: " + qid + " query: " + query);
              queries.add(qid);
              
              ScoreList r = null;
              r = processQuery(query, model);
              
              if (r != null) {
                  int count = 0;
                  while(count < fbDocs && count < r.size()) {
                      docs.add(new DocScore(r.getDocid(count), r.getDocidScore(count)));
                      count++;
                  }
                  initialDocs.add(docs);
              } else {
                  System.out.println("Error: there are not enough retrieved documents for expanded query.");
              }
              
          }
      } catch (IOException e) {
          e.printStackTrace();
      } finally {
          input.close();
      }
      return initialDocs;
  }
  
  static void processExpandedQuery(String queryFilePath, ArrayList<String> expandedQuery, String trecEvalOutputPath, String trecEvalOutputLength, 
          RetrievalModel model, double fbOrigWeight) throws IOException {
      BufferedReader input = null;
      int index = 0;        // fetch ith expanded query composed of expanded terms

      try {
        String qLine = null;
        input = new BufferedReader(new FileReader(queryFilePath));

        //  Each pass of the loop processes one query.
        while ((qLine = input.readLine()) != null) {
          int d = qLine.indexOf(':');

          if (d < 0) {
            throw new IllegalArgumentException("Syntax error:  Missing ':' in query line.");
          }

          printMemoryUsage(false);

          // obtain the expanded query
          String qid = qLine.substring(0, d);
          String query = qLine.substring(d + 1);
          String q = expandedQuery.get(index);
          StringBuilder sb = new StringBuilder();
          sb.append("#wand (");
          sb.append(fbOrigWeight);
          sb.append(" #and (");
          sb.append(query);
          sb.append(") ");
          sb.append(1 - fbOrigWeight);
          sb.append(" ");
          sb.append(q);
          sb.append(")");
          String expandedQ = sb.toString();
//          System.out.println("processExpandedQuery:: expanded query: " + qid + " " + expandedQ);

          ScoreList r = null;

          r = processQuery(expandedQ, model);

          if (r != null) {
            printResults(qid, r, trecEvalOutputLength, trecEvalOutputPath);
            System.out.println();
          }
          index++;
        }
      } catch (IOException ex) {
        ex.printStackTrace();
      } finally {
        input.close();
      }
  }
  
  /**
   *  Process the query file.
   *  @param queryFilePath
   *  @param model
   *  @throws IOException Error accessing the Lucene index.
   */
  // changed. The original one is static void processQF(String queryFilePath, RM model)
  static void processQueryFile(String queryFilePath, String trecEvalOutputPath, String trecEvalOutputLength, 
                               RetrievalModel model)
      throws IOException {

    BufferedReader input = null;

    try {
      String qLine = null;

      input = new BufferedReader(new FileReader(queryFilePath));

      //  Each pass of the loop processes one query.

      while ((qLine = input.readLine()) != null) {
        int d = qLine.indexOf(':');

        if (d < 0) {
          throw new IllegalArgumentException
            ("Syntax error:  Missing ':' in query line.");
        }

        printMemoryUsage(false);

        String qid = qLine.substring(0, d);
        String query = qLine.substring(d + 1);

        System.out.println("Query " + qLine);

        ScoreList r = null;

//        System.out.println("query: " + query);
        r = processQuery(query, model);

        if (r != null) {
          printResults(qid, r, trecEvalOutputLength, trecEvalOutputPath);
          System.out.println();
        }
      }
    } catch (IOException ex) {
      ex.printStackTrace();
    } finally {
      input.close();
    }
  }

  /**
   * Print the query results.
   * 
   * THIS IS NOT THE CORRECT OUTPUT FORMAT. YOU MUST CHANGE THIS METHOD SO
   * THAT IT OUTPUTS IN THE FORMAT SPECIFIED IN THE HOMEWORK PAGE, WHICH IS:
   * 
   * QueryID Q0 DocID Rank Score RunID
   * 
   * @param queryName
   *          Original query.
   * @param result
   *          A list of document ids and scores
   * @throws IOException Error accessing the Lucene index.
   */
  static void printResults(String queryName, ScoreList result, String trecEvalOutputLength, 
          String trecEvalOutputPath) throws IOException {
//  static void printResults(String queryName, ScoreList result) throws IOException {

      File file = new File(trecEvalOutputPath);
      FileWriter writer = new FileWriter(file, true);
      if (result.size() < 1) {
          String s = queryName + "  Q0  dummy  1  0  fubar\n";
          System.out.println(s);
          writer.write(s);
          writer.flush();
      } else {
          int length = Math.min(Integer.parseInt(trecEvalOutputLength), result.size());
          for (int i = 0; i < length; i++) {
              String score = String.format("%.18f", result.getDocidScore(i));
              String s = queryName + "  Q0  " + Idx.getExternalDocid(result.getDocid(i)) + "  " 
                      + (i + 1) + "  " + score + "  fubar\n";
              System.out.println(s);
              writer.write(s);
              writer.flush();
          }
      }
      writer.close();
  }

  static List<ArrayList<DocScore>> getInitialDoc(String fbInitialRankingFile, int fbDocs, List<Integer> queries) throws Exception {
      ArrayList<DocScore> docs = new ArrayList<DocScore>();
      List<ArrayList<DocScore>> initialDocs = new ArrayList<ArrayList<DocScore>>();
      
      File initialFile = new File(fbInitialRankingFile);
      if (!initialFile.canRead()) {
          throw new IllegalArgumentException("Cannot read " + fbInitialRankingFile);
      }
      
      int prevId = 0;
      int n = 0;
      Scanner scan = new Scanner(initialFile);
      String line = null;
      do {
          line = scan.nextLine();
//          System.out.println(line);
          String[] docInfo = line.split(" +");
          int queryId = Integer.parseInt(docInfo[0]);
//          String exDocId = docInfo[2];
          int docId = Idx.getInternalDocid(docInfo[2]);
          double score = Double.parseDouble(docInfo[4]);
//          System.out.println("queryId: " + queryId + " exDocId: " + docId + " score: " + score);
          
          if (queryId != prevId) {
              queries.add(queryId);
              docs = new ArrayList<DocScore>();
              initialDocs.add(docs);
              n = 0;
          }
          prevId = queryId;
          if (n < fbDocs) {
              docs.add(new DocScore(docId, score));
              n++;
          }
      } while(scan.hasNext());
      
      scan.close();
      return initialDocs;
  }
  /**
   *  Read the specified parameter file, and confirm that the required
   *  parameters are present.  The parameters are returned in a
   *  HashMap.  The caller (or its minions) are responsible for processing
   *  them.
   *  @return The parameters, in <key, value> format.
   */
  private static Map<String, String> readParameterFile (String parameterFileName)
    throws IOException {

    Map<String, String> parameters = new HashMap<String, String>();

    File parameterFile = new File (parameterFileName);

    if (! parameterFile.canRead ()) {
      throw new IllegalArgumentException
        ("Can't read " + parameterFileName);
    }

    Scanner scan = new Scanner(parameterFile);
    String line = null;
    do {
      line = scan.nextLine();
      System.out.println(line);
      String[] pair = line.split ("=");
      System.out.println(Arrays.toString(pair));
      parameters.put(pair[0].trim(), pair[1].trim());
    } while (scan.hasNext());

    scan.close();

    if (!parameters.containsKey("trecEvalOutputLength")) {
        parameters.put("trecEvalOutputLength", "100");
    }
    
    if (! (parameters.containsKey("indexPath") &&
           parameters.containsKey("queryFilePath") &&
           parameters.containsKey("trecEvalOutputPath") &&
           parameters.containsKey("retrievalAlgorithm"))) {
      throw new IllegalArgumentException
        ("Required parameters were missing from the parameter file.");
    }

    return parameters;
  }
}

