/*
 *  Copyright (c) 2017, Carnegie Mellon University.  All Rights Reserved.
 *  Version 3.3.1.
 */
import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
  
  private static boolean scaling = false;


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
//    System.out.println(parameters.size());
//    for (Map.Entry<String, String> entry : parameters.entrySet()) {
//        System.out.println(entry.getKey() + ": " + entry.getValue());
//    }
    //  Open the index and initialize the retrieval model.

    Idx.open (parameters.get ("indexPath"));
    RetrievalModel model = null;
    if (parameters.containsKey("retrievalAlgorithm")) {
        model = initializeRetrievalModel (parameters);
    }
    

    //  Perform experiments.
//    System.out.println(parameters.get("diversity"));
    // Diversity is performed.
    if (parameters.containsKey("diversity") && parameters.get("diversity").equals("true")) {
        Map<String, List<DocScore>> initRanking = new HashMap<String, List<DocScore>>();
        String maxInputLength = parameters.get("diversity:maxInputRankingsLength");
        String queryFile = parameters.get("queryFilePath");
        String intentFile = parameters.get("diversity:intentsFile");
        Map<String, List<String>> queries = new HashMap<String, List<String>>();
        Map<Integer, List<IntentScore>> docIntentScore = new HashMap<Integer, List<IntentScore>>();
        Map<String, List<DocScore>> diversifiedRanking = null;
        getQueryInfo(queryFile, queries);
        getQueryInfo(intentFile, queries);
        Helper.printList(queries);
//        System.out.println(queries.size());
        if (parameters.containsKey("diversity:initialRankingFile")) {
            String initRankingFile = parameters.get("diversity:initialRankingFile");
            processInitRankingFile(initRankingFile, maxInputLength, initRanking, docIntentScore, queries);
//            Helper.printList(queries);
        } else {
            initRanking = new HashMap<String, List<DocScore>>();
            Map<String, List<DocScore>> intermediate = null;
            intermediate = getInitialRanking(queryFile, maxInputLength, model, docIntentScore);
            if (intermediate != null && intermediate.size() > 0) {
                initRanking.putAll(intermediate);
            }
            intermediate = getInitialRanking(intentFile, maxInputLength, model, docIntentScore, initRanking);
            if (intermediate != null && intermediate.size() > 0) {
                initRanking.putAll(intermediate);
            }
        }
//        Helper.printList(queries);
//        String inputFilePath = parameters.get("diversity:inputFilePath");
//        Helper.printResults(initRanking, inputFilePath);
        System.out.println("Get initial ranking completed.");
//        String intentFilePath = parameters.get("diversity:intentFilePath");
//        Helper.printIntentScore(docIntentScore, intentFilePath);
        
//        System.out.println();
        String algorithm = parameters.get("diversity:algorithm");
        String divLambda = parameters.get("diversity:lambda");
        String maxResultLength = parameters.get("diversity:maxResultRankingLength");
        String trecEvalOutputPath = parameters.get("trecEvalOutputPath");
        Map<Integer, List<IntentScore>> normalizedRanking = null;
        if (scaling) {
            normalizedRanking = normalizeRanking(initRanking, docIntentScore, queries);
            System.out.println("Scaling completed.");
        } else {
            normalizedRanking = docIntentScore;
        }
//        Helper.printIntentScore(normalizedRanking);
//        // Use the diversity:algorithm to produce a diversified ranking 
        
        if (algorithm.equals("xQuAD")) {
            diversifiedRanking = xQuAD(initRanking, divLambda, queries, maxResultLength, normalizedRanking);
            System.out.println("xQuAD completed.");
        } else if (algorithm.equals("PM2")) {
            diversifiedRanking = PM2(initRanking, divLambda, queries, maxResultLength, normalizedRanking);
        }
        Helper.printResults(diversifiedRanking, trecEvalOutputPath);
        
        
    } else {
      processQueryFile(parameters.get("queryFilePath"), parameters.get("trecEvalOutputPath"), 
              parameters.get("trecEvalOutputLength"), model);
    }

    //  Clean up.
    
    timer.stop ();
    System.out.println ("Time:  " + timer);
  }

  private static void getQueryInfo(String filePath, Map<String, List<String>> queries) throws Exception {
      BufferedReader input = null;
      try {
          input = new BufferedReader(new FileReader(filePath));
          String line = null;
          while((line = input.readLine()) != null) {
              int index = line.indexOf(':');
//              System.out.println(line);
              String query = line.substring(0, index);
              index = query.indexOf('.');
              if (index < 0) {
                  queries.put(query, new LinkedList<String>());
              } else {
                  List<String> list = queries.get(query.substring(0, index));
                  list.add(query);
                  queries.put(query.substring(0, index), list);
              }
          }
      } catch (Exception e) {
          e.printStackTrace();
      } finally {
          input.close();
      }
  }
  
  private static Map<String, List<DocScore>> PM2(Map<String, List<DocScore>> ranking, String lamb, 
          Map<String, List<String>> queries, String maxResultRanking, Map<Integer, List<IntentScore>> docIntentScore) throws IOException {
      Map<String, List<DocScore>> diversifiedRanking = new HashMap<String, List<DocScore>>();
      for (Map.Entry<String, List<String>> entry : queries.entrySet()) {
          List<DocScore> inDivRanking = new LinkedList<DocScore>();         // store the diversified ranking for individual query
          String origQueryId = entry.getKey();
//          System.out.println("Original query ID: " + origQueryId);
          List<String> queryIntents = entry.getValue();
          double lambda = Double.parseDouble(lamb);
          double maxOutput = Double.parseDouble(maxResultRanking);
//          System.out.println("max output length: " + maxOutput);
          double initV = maxOutput / (double)queryIntents.size();
          List<PM2Values> pm2 = new ArrayList<PM2Values>();
          Map<String, PM2Values> intentValues = new HashMap<String, PM2Values>();
          for (int i = 0; i < queryIntents.size(); i++) {
              pm2.add(new PM2Values(queryIntents.get(i), initV, 0d, 0d));
              intentValues.put(queryIntents.get(i), new PM2Values(initV, 0d));
          }
//          System.out.print("Original values: ");
//          Helper.printPM2(intentValues);
          List<DocScore> originalRanking = ranking.get(origQueryId);
          int count = 0;
          while (count < maxOutput) {
              List<DocScore> docs = new LinkedList<DocScore>();     // store the document scores each iteration to find the maximum one.
              String desireIntent = null;
              double max = Double.MIN_VALUE;
              // calculate qt for each intent, find the query intent that must be covered next at the same time.
              for (int i = 0; i < pm2.size(); i++) {
                  PM2Values currentPM2 = pm2.get(i);
                  String currentIntent = currentPM2.intentId;
//                  System.out.print(" current intent: " + currentIntent);
                  double qt = currentPM2.v / (2 * currentPM2.s + 1);
                  if (qt > max) {
                      desireIntent = currentIntent;
                      max = qt;
//                      System.out.println(" desired intent: " + desireIntent);
                  }
                  pm2.set(i, new PM2Values(currentIntent, currentPM2.v, currentPM2.s, qt));
                  intentValues.put(currentIntent, new PM2Values(currentIntent, currentPM2.v, currentPM2.s, qt));
              }
//              System.out.println(" desired intent: " + desireIntent);
              
              // calculate score for each document
              for (int i = 0; i < originalRanking.size(); i++) {
                  DocScore doc = originalRanking.get(i);
                  int internalId = doc.getDocID();
                  List<IntentScore> intentScore = docIntentScore.get(internalId);
                  double coverScore = 0d;       // cover the specified intent.
                  double extraScore = 0d;       // extra credit for covering other intents.
                  for (IntentScore qi : intentScore) {
                      String intentId = qi.intentId;
//                      System.out.println("Current intent: " + intentId);
                     
                      int index = intentId.indexOf('.');
//                      if (index < 0) {
//                          continue;
//                      }
                      if (index < 0 || !intentId.subSequence(0, index).equals(origQueryId)) {
                          continue;
                      }
//                      System.out.println(Idx.getExternalDocid(internalId));
                      double qti = intentValues.get(intentId).qt;
                      if (intentId.equals(desireIntent)) {
                          coverScore = lambda * qti * qi.score;
//                          System.out.println("Cover score: " + coverScore);
                      } else {              // extra credit
                          double pDjPi = qi.score;
                          double tmp = qti * pDjPi;
                          extraScore += tmp;
                      }
                  }
                  double score = coverScore + (1 - lambda) * extraScore;
//                  System.out.println(Idx.getExternalDocid(internalId) + ", " + score);
                  docs.add(new DocScore(internalId, score));
              }
              Collections.sort(docs, new DocScore());
              DocScore maxDoc = docs.get(0);
//              System.out.println("Max document: " + Idx.getExternalDocid(maxDoc.getDocID()) + ", score: " + maxDoc.getDocScore());
              // Add d to the diversified ranking
              
              if (maxDoc.getDocScore() < 0.0000000000000000009) {
//                  System.out.println("Coming to 0: remaining length: " + originalRanking.size() + " current query:" + origQueryId);
                  double maxScore = inDivRanking.get(inDivRanking.size() - 1).getDocScore();
//                  double maxScore = maxDoc.getDocScore();
                  for (int i = 0; i < originalRanking.size() && count < maxOutput; i++) {
                      DocScore ds = originalRanking.get(i);
                      inDivRanking.add(new DocScore(ds.getDocID(), maxScore * 0.9));
                      maxScore = maxScore * 0.9;
                      count++;
                  }
                  break;
              }
              inDivRanking.add(maxDoc);
              // Remove d from the initial ranking
              for (int i = 0; i < originalRanking.size(); i++) {
                  if (originalRanking.get(i).getDocID() == maxDoc.getDocID()) {
                      List<DocScore> tmp1 = originalRanking.subList(0, i);
                      List<DocScore> tmp2 = originalRanking.subList(i + 1, originalRanking.size());
                      originalRanking = new LinkedList<DocScore>();
                      originalRanking.addAll(tmp1);
                      originalRanking.addAll(tmp2);
                      break;
                  }
              }
//              System.out.println("Original Ranking size: " + originalRanking.size());
              // Update si
//              System.out.println("Updating si");
              int internalDocId = maxDoc.getDocID();
              List<IntentScore> intents = docIntentScore.get(internalDocId);
//              System.out.print(Idx.getExternalDocid(internalDocId) + ": ");
//              for (int i = 0; i < intents.size(); i++) {
//                  System.out.print(intents.get(i).intentId + ", " + intents.get(i).score + " ");
//              }
//              System.out.println();
              double sum = 0d;                              // sum of p(d*|qj)
              for (int i = 0; i < intents.size(); i++) {
                 
                  
                  IntentScore intS = intents.get(i);
                  String q = intS.intentId;
                  int index = q.indexOf('.');
                  if (index >= 0 && q.substring(0, index).equals(origQueryId)) {
//                      if (origQueryId.equals("116")) {
//                          System.out.println("intent size: " + intents.size() + q);
//                      }
                      sum += intents.get(i).score;
                  }
                  
              }
//              System.out.println("Sum: " + sum);
              for (int i = 0; i < pm2.size(); i++) {
                  PM2Values updatePM2 = pm2.get(i);
//                  System.out.println(updatePM2.intentId + ", " + updatePM2.v + ", " + updatePM2.s + ", " + updatePM2.qt);
                  String queryIntentId = updatePM2.intentId;
                  double s = updatePM2.s;
                  double pDQi = 0d;                     // p(d*|qi)
                  for (int j = 0; j < intents.size(); j++) {
                      IntentScore intS = intents.get(j);
                      String docIntentId = intS.intentId;
                      if (docIntentId.equals(queryIntentId)) {
                          pDQi = intS.score;
//                          System.out.println("Match: " + docIntentId + ", " + pDQi);
                          break;
                      }
                  }
                  updatePM2.s = s + pDQi / sum;
//                  System.out.println("Updated: " + updatePM2.s);
//                  System.out.println();
                  pm2.set(i, updatePM2);
                  intentValues.put(queryIntentId, new PM2Values(queryIntentId, updatePM2.v, updatePM2.s, updatePM2.qt));
              }
              for (int i = 0; i < pm2.size(); i++) {
                  PM2Values p = pm2.get(i);
//                  System.out.println(p.intentId + ": " + p.v + ", " + p.s + ", " + p.qt);
              }
//              Helper.printPM2(intentValues);
              count++;
          }
          Collections.sort(inDivRanking, new DocScore());
          diversifiedRanking.put(origQueryId, inDivRanking);
      }
      return diversifiedRanking;
  }
  
  private static Map<String, List<DocScore>> xQuAD(Map<String, List<DocScore>> ranking, String lambda, 
          Map<String, List<String>> queries, String maxResultRanking, Map<Integer, List<IntentScore>> docIntentScore) throws IOException {
      Map<String, List<DocScore>> diversifiedRanking = new HashMap<String, List<DocScore>>();
      // For each query and its corresponding query intents.
      for (Map.Entry<String, List<String>> entry : queries.entrySet()) {
          List<DocScore> inDivRanking = new LinkedList<DocScore>();     // store the diversified document ranking.
          String origQueryId = entry.getKey();
          List<DocScore> origRanking = ranking.get(origQueryId);
          List<String> queryIntents = entry.getValue();
          double lamb = Double.parseDouble(lambda);
          double weight = 1d / queryIntents.size();              // p(qi|q)
          int maxOutputLength = Integer.parseInt(maxResultRanking);
          int count = 0;
          while (count < maxOutputLength) {
              List<DocScore> docs = new LinkedList<DocScore>();
              for (int i = 0; i < origRanking.size(); i++) {
                  DocScore doc = origRanking.get(i);            // d
                  int internalId = doc.getDocID();
                  double origScore = 0d;         //p(d|q)
                  double divScore = 0d;
                  double score = 0d;
                  List<IntentScore> currDoc = docIntentScore.get(internalId);
                  for (IntentScore qi : currDoc) {
                      String intentId = qi.intentId;
                      if (intentId.equals(origQueryId)) {
                          origScore = qi.score;
                          break;
                      }
                  }
                  double relScore = (1 - lamb) * origScore;     // relevance score
                  for (IntentScore qi : currDoc) {
                      String intentId = qi.intentId;
                      int index = intentId.indexOf('.');
                      if (index < 0 || (index >= 0 && !intentId.subSequence(0, index).equals(origQueryId))) {
                          continue;
                      }
                      double pDQi = qi.score;                   // p(d|qi)
                      double penalty = 1d;
                      for (DocScore d : inDivRanking) {                 // previous covered --> multiply of p(dj|qi)
                          int id = d.getDocID();
                          List<IntentScore> intL = docIntentScore.get(id);
                          for (int j = 0; j < intL.size(); j++) {
                              IntentScore intS = intL.get(j);
                              String intentQuery = intS.intentId;
                              if (intentQuery.equals(intentId)) {
                                  double tmp = 1 - intS.score;
                                  penalty *= tmp;
                                  break;
                              }
                          }
                      }
                      divScore = divScore + pDQi * penalty;
                  }
                  
                  score = relScore + lamb * weight * divScore;
                  docs.add(new DocScore(internalId, score));
              }
              Collections.sort(docs, new DocScore());
              DocScore max = docs.get(0);
              inDivRanking.add(max);
              for (int i = 0; i < origRanking.size(); i++) {
                  if (origRanking.get(i).getDocID() == max.getDocID()) {
                      List<DocScore> tmp1 = origRanking.subList(0, i);
                      List<DocScore> tmp2 = origRanking.subList(i + 1, origRanking.size());
                      origRanking = new LinkedList<DocScore>();
                      origRanking.addAll(tmp1);
                      origRanking.addAll(tmp2);
                      break;
                  }
              }
              
              count++;
          }
          diversifiedRanking.put(origQueryId, inDivRanking);
          
      }
      return diversifiedRanking;
  }
  
  private static Map<Integer, List<IntentScore>> normalizeRanking(Map<String, List<DocScore>> initialRanking,
          Map<Integer, List<IntentScore>> docIntentScore, Map<String, List<String>> queries) throws IOException {
      Map<Integer, List<IntentScore>> normalized = new HashMap<Integer, List<IntentScore>>();
      Map<String, Double> maxSumScores = new HashMap<String, Double>();
      
      Map<String, Double> sumScores = new HashMap<String, Double>();        // query intent id/score pair
      // calculate the sum scores.
      for (Map.Entry<String, List<String>> entry : queries.entrySet()) {
          String qid = entry.getKey();
          List<DocScore> origRanking = initialRanking.get(qid);
//          System.out.println("ORIGINAL SIZE::: " + origRanking.size());
          for (int i = 0; i < origRanking.size(); i++) {
              DocScore doc = origRanking.get(i);
              int internalId = doc.getDocID();
              // intent list may contain query intents that don't belong to current original query id since
              // I put all initial ranking documents together.
              List<IntentScore> intentList = docIntentScore.get(internalId);
              int special = 0;
              for (int j = 0; j < intentList.size(); j++) {
                  IntentScore intScore = intentList.get(j);
                  String intentId = intScore.intentId;
                  int index = intentId.indexOf('.');
                  if (index < 0 && !intentId.equals(qid) || (index >= 0 && !intentId.substring(0, index).equals(qid))) {
                      special = internalId;
                      continue;
                  }
                  double intentScore = intScore.score;
                  sumScores.put(intentId, sumScores.getOrDefault(intentId, 0d) + intentScore);
              }
          }
      }
      Helper.printMap(sumScores);
      // find the maximum score
      for (Map.Entry<String, Double> entry : sumScores.entrySet()) {
          String queryId = entry.getKey();
          Double score = entry.getValue();
          int index = queryId.indexOf('.');
          if (index >= 0) {
              maxSumScores.put(queryId.substring(0, index), 
                      Math.max(score, maxSumScores.getOrDefault(queryId.substring(0, index), Double.MIN_VALUE)));
          }
          else {
              maxSumScores.put(queryId, Math.max(score, maxSumScores.getOrDefault(queryId, Double.MIN_VALUE)));
          }
      }
      Helper.printMap(maxSumScores);
      // scale the document scores
      
      for (Map.Entry<String, List<String>> entry : queries.entrySet()) {
          String qid = entry.getKey();
          List<DocScore> origRanking = initialRanking.get(qid);
          for (int i = 0; i < origRanking.size(); i++) {
              DocScore doc = origRanking.get(i);
              int internalId = doc.getDocID();
              List<IntentScore> intentList = docIntentScore.get(internalId);
              List<IntentScore> normalizedList = new LinkedList<IntentScore>();
              int special = 0;
              for (int j = 0; j < intentList.size(); j++) {
                  IntentScore intScore = intentList.get(j);
                  String intentId = intScore.intentId;
                  double intentScore = intScore.score;
                  int index = intentId.indexOf('.');
                  if (index < 0 && !intentId.equals(qid) || (index >= 0 && !intentId.substring(0, index).equals(qid))) {
                      special = internalId;
                      continue;
                  }
                  double max = 0d;
                  if (index >= 0) {
                      max = maxSumScores.get(intentId.substring(0, index));
                      normalizedList.add(new IntentScore(intentId, intentScore / max));
                  }
                  else {
                      max = maxSumScores.get(intentId);
                      normalizedList.add(new IntentScore(intentId, intentScore / max));
                  }
              }
              normalized.put(internalId, normalizedList);
          }
      }
      return normalized;
  }
  
  private static void processInitRankingFile(String initRankingFile, String maxInputLength, Map<String, List<DocScore>> initRanking,
          Map<Integer, List<IntentScore>> docIntentScore, Map<String, List<String>> queries) throws Exception {
      FileReader reader = null;
      BufferedReader input = null;
//      Map<String, List<DocScore>> initRanking = new HashMap<String, List<DocScore>>();
      int max = Integer.parseInt(maxInputLength);
      try {
          reader = new FileReader(initRankingFile);
          input = new BufferedReader(reader);
          int count = 1;
          String line = null;
          String prev = null;
          List<DocScore> docScore = null;
          while ((line = input.readLine()) != null) {
              String[] info = line.split(" +");
              String qid = info[0];
//              System.out.println(qid);
              if (prev == null || !prev.equals(qid)) {
                  docScore = new LinkedList<DocScore>();
                  initRanking.put(qid, docScore);
                  prev = qid;
                  count = 1;
                  max = Integer.parseInt(maxInputLength);
              }
              int index = qid.indexOf('.');
              if (index >= 0) {
                  List<DocScore> list = initRanking.get(qid.substring(0, index));
                  if (list != null) {
                      max = list.size();
                  }
              }
//              System.out.println(qid + ", " + max);
              if (count <= max) {
                  int docId = Idx.getInternalDocid(info[2]);
                  double score = Double.parseDouble(info[4]);
                  if (score >= 1) {
                      scaling = true;
                  }
                  docScore.add(new DocScore(docId, score));
                  count++;
                  List<IntentScore> list = docIntentScore.getOrDefault(docId, new LinkedList<IntentScore>());
                  list.add(new IntentScore(qid, score));
                  docIntentScore.put(docId, list);
              }
          }
      } catch (Exception e) {
          e.printStackTrace();
      } finally {
          reader.close();
          input.close();
      }
  }
  
  /**
   *  Allocate the retrieval model and initialize it using parameters
   *  from the parameter file.
   *  @return The initialized retrieval model
   *  @throws IOException Error accessing the Lucene index.
   */
  private static RetrievalModel initializeRetrievalModel (Map<String, String> parameters)
    throws IOException {

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

  private static Map<String, List<DocScore>> getInitialRanking(String filePath, String maxInputLength, 
          RetrievalModel model, Map<Integer, List<IntentScore>> docIntentScore) throws Exception {
      FileReader reader = null;
      BufferedReader input = null;
      Map<String, List<DocScore>> initRanking = new HashMap<String, List<DocScore>>();
      try {
          reader = new FileReader(filePath);
          input = new BufferedReader(reader);
          String line = null;
          Map<String, List<DocScore>> tmp = null;
          while ((line = input.readLine()) != null) {
              tmp = processQuery(line, model, maxInputLength, docIntentScore);
              if (tmp != null) {
                  initRanking.putAll(tmp);
              }
          }
      } catch (Exception e) {
          e.printStackTrace();
      } finally {
          reader.close();
          input.close();
      }
      return initRanking;
  }
  
  private static Map<String, List<DocScore>> getInitialRanking(String filePath, String maxInputLength, 
          RetrievalModel model, Map<Integer, List<IntentScore>> docIntentScore, 
          Map<String, List<DocScore>> init) throws Exception {
      FileReader reader = null;
      BufferedReader input = null;
      Map<String, List<DocScore>> initRanking = new HashMap<String, List<DocScore>>();
      try {
          reader = new FileReader(filePath);
          input = new BufferedReader(reader);
          String line = null;
          Map<String, List<DocScore>> tmp = null;
          while ((line = input.readLine()) != null) {
              int index = line.indexOf('.');
              if (index >= 0) {
                  maxInputLength = String.valueOf(init.get(line.substring(0, index)).size());
              }
              tmp = processQuery(line, model, maxInputLength, docIntentScore);
              if (tmp != null) {
                  initRanking.putAll(tmp);
              }
          }
      } catch (Exception e) {
          e.printStackTrace();
      } finally {
          reader.close();
          input.close();
      }
      return initRanking;
  }
  
  private static Map<String, List<DocScore>> processQuery(String line, RetrievalModel model, String maxInputLength,
          Map<Integer, List<IntentScore>> docIntentScore) throws Exception {
      List<DocScore> list = new LinkedList<DocScore>();
      Map<String, List<DocScore>> map = null;
      int d = line.indexOf(':');
      if (d < 0) {
          throw new IllegalArgumentException("Syntax error:  Missing ':' in query line.");
      }
      printMemoryUsage(false);
      String qid = line.substring(0, d);
      String query = line.substring(d + 1);
      System.out.println("Query " + line);
      ScoreList r = null;
      r = processQuery(query, model);
      if (r != null) {
          int max = Integer.parseInt(maxInputLength);
          map = new HashMap<String, List<DocScore>>();
//          System.out.println(qid + ", " + max);
          for (int i = 0; i < max && i < r.size(); i++) {
              if (r.getDocidScore(i) >= 1) {
                  scaling = true;
              }
              list.add(new DocScore(r.getDocid(i), r.getDocidScore(i)));
              List<IntentScore> intentList = docIntentScore.getOrDefault(r.getDocid(i), new LinkedList<IntentScore>());
              intentList.add(new IntentScore(qid, r.getDocidScore(i)));
              docIntentScore.put(r.getDocid(i), intentList);
          }
          map.put(qid, list);
      }
      return map;
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

      // changed
//      System.out.println("trecEvalOutputPath is: " + trecEvalOutputPath);
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
           parameters.containsKey("trecEvalOutputPath"))) {
      throw new IllegalArgumentException
        ("Required parameters were missing from the parameter file.");
    }
    if (!parameters.containsKey("diversity:initialRankingFile") && !parameters.containsKey("retrievalAlgorithm")) {
        throw new IllegalArgumentException ("Required parameters were missing from the parameter file.");
    }

//    for (Map.Entry<String, String> entry : parameters.entrySet()) {
//        System.out.println(entry.getKey() + ": " + entry.getValue());
//    }
    return parameters;
  }

}
