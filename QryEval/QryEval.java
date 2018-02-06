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

    //  Open the index and initialize the retrieval model.

    Idx.open (parameters.get ("indexPath"));
    RetrievalModel model = initializeRetrievalModel (parameters);

    //  Perform experiments.
    // if feedback(fb) is missing from the parameter file or fb is set to false, use
    // the original query to retrieve documents.
//    System.out.println("fb: " + parameters.get("fb"));
    if (!parameters.containsKey("fb") || parameters.get("fb").equals("false")) {
        processQueryFile(parameters.get("queryFilePath"), parameters.get("trecEvalOutputPath"), 
                parameters.get("trecEvalOutputLength"), model);
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
//        System.out.println("size of initial documents: " + initialDocs.size());
        
        String fbExpansionQueryFile = parameters.get("fbExpansionQueryFile");
        int fbTerms = Integer.parseInt(parameters.get("fbTerms"));
        int fbMu = Integer.parseInt(parameters.get("fbMu"));
        ArrayList<String> expandedQuery = queryExpansion(fbExpansionQueryFile, fbTerms, fbMu, fbOrigWeight, initialDocs, queryID);
        processExpandedQuery(parameters.get("queryFilePath"), expandedQuery, parameters.get("trecEvalOutputPath"), 
                parameters.get("trecEvalOutputLength"), model, fbOrigWeight);
    }
    

    //  Clean up.
    
    timer.stop ();
    System.out.println ("Time:  " + timer);
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

