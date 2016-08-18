/*
 *  Copyright (c) 2016, Carnegie Mellon University.  All Rights Reserved.
 *  Version 3.1.1.
 */

import java.io.*;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.*;
import java.util.Map.Entry;

import org.apache.lucene.analysis.Analyzer.TokenStreamComponents;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

/**
 * QryEval is a simple application that reads queries from a file,
 * evaluates them against an index, and writes the results to an
 * output file.  This class contains the main method, a method for
 * reading parameter and query files, initialization methods, a simple
 * query parser, a simple query processor, and methods for reporting
 * results.
 * <p>
 * This software illustrates the architecture for the portion of a
 * search engine that evaluates queries.  It is a guide for class
 * homework assignments, so it emphasizes simplicity over efficiency.
 * Everything could be done more efficiently and elegantly.
 * <p>
 * The {@link Qry} hierarchy implements query evaluation using a
 * 'document at a time' (DaaT) methodology.  Initially it contains an
 * #OR operator for the unranked Boolean retrieval model and a #SYN
 * (synonym) operator for any retrieval model.  It is easily extended
 * to support additional query operators and retrieval models.  See
 * the {@link Qry} class for details.
 * <p>
 * The {@link RetrievalModel} hierarchy stores parameters and
 * information required by different retrieval models.  Retrieval
 * models that need these parameters (e.g., BM25 and Indri) use them
 * very frequently, so the RetrievalModel class emphasizes fast access.
 * <p>
 * The {@link Idx} hierarchy provides access to information in the
 * Lucene index.  It is intended to be simpler than accessing the
 * Lucene index directly.
 * <p>
 * As the search engine becomes more complex, it becomes useful to
 * have a standard approach to representing documents and scores.
 * The {@link ScoreList} class provides this capability.
 */
@SuppressWarnings("deprecation")
public class QryEval {

  //  --------------- Constants and variables ---------------------

  private static final String USAGE =
    "Usage:  java QryEval paramFile\n\n";

  private static final EnglishAnalyzerConfigurable ANALYZER =
    new EnglishAnalyzerConfigurable(Version.LUCENE_43);
  private static final String[] TEXT_FIELDS =
    { "body", "title", "url", "inlink" };
  private static String CurrQueryID;
  private static String exact_original_query;

  //  --------------- Methods ---------------------------------------

  /**
   * @param args The only argument is the parameter file name.
   * @throws Exception Error accessing the Lucene index.
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

    //  Configure query lexical processing to match index lexical
    //  processing.  Initialize the index and retrieval model.

    ANALYZER.setLowercase(true);
    ANALYZER.setStopwordRemoval(true);
    ANALYZER.setStemmer(EnglishAnalyzerConfigurable.StemmerType.KSTEM);

    Idx.initialize (parameters.get ("indexPath"));
    RetrievalModel model = initializeRetrievalModel (parameters);

    // If the model is letor, then redirect to this module
    if(parameters.get("retrievalAlgorithm").equals("letor")){

    	System.out.println("LTR_DBG: Entering LTR module");
    	LTR l = new LTR();
    	l.MainLTR(parameters);
    	
    }else{
    	processQueryFile(parameters, model);
    }

    //  Clean up.
    timer.stop ();
    System.out.println ("Time:  " + timer);
  }

  /**
   * Allocate the retrieval model and initialize it using parameters
   * from the parameter file.
   * @return The initialized retrieval model
   * @throws IOException Error accessing the Lucene index.
   */
  private static RetrievalModel initializeRetrievalModel (Map<String, String> parameters)
    throws IOException {

    RetrievalModel model = null;
    String modelString = parameters.get ("retrievalAlgorithm").toLowerCase();

    if (modelString.equals("unrankedboolean")) {
      model = new RetrievalModelUnrankedBoolean();
    }else if(modelString.equals("rankedboolean")){
      model = new RetrievalModelRankedBoolean();
    }else if(modelString.equals("bm25")){
      model = new RetrievalModelBM25(
    		  Double.parseDouble(parameters.get("BM25:k_1")),
    		  Double.parseDouble(parameters.get("BM25:b")),
    		  Double.parseDouble(parameters.get("BM25:k_3"))
    		  );
      	model.printParams(); // Debug purpose
    }else if(modelString.equals("indri")){
    	
    	if(parameters.containsKey("fb") && parameters.get("fb").equals("true")){
    		
    		model = new RetrievalModelIndri(
    		      Double.parseDouble(parameters.get("Indri:lambda")),
    	          Double.parseDouble(parameters.get("Indri:mu")),
  	      		  Integer.parseInt(parameters.get("fbDocs")),
  	      		  Integer.parseInt(parameters.get("fbTerms")),
  	      		  Double.parseDouble(parameters.get("fbMu")),
  	      		  Double.parseDouble(parameters.get("fbOrigWeight"))
  	      		  );
    	}else{
    		model = new RetrievalModelIndri(
    	      		  Double.parseDouble(parameters.get("Indri:lambda")),
    	      		  Double.parseDouble(parameters.get("Indri:mu"))
    	      		  );
    	}
        model.printParams(); // Debug purpose
      }
    else if(modelString.equals("letor")){
    	
    }else {
      throw new IllegalArgumentException
        ("Unknown retrieval model " + parameters.get("retrievalAlgorithm"));
    }

    return model;
  }

  /**
   * Optimize the query by removing degenerate nodes produced during
   * query parsing, for example '#NEAR/1 (of the)' which turns into 
   * '#NEAR/1 ()' after stopwords are removed; and unnecessary nodes
   * or subtrees, such as #AND (#AND (a)), which can be replaced by 'a'.
   */
  static Qry optimizeQuery(Qry q) {

    //  Term operators don't benefit from optimization.

    if (q instanceof QryIopTerm) {
      return q;
    }

    //  Optimization is a depth-first task, so recurse on query
    //  arguments.  This is done in reverse to simplify deleting
    //  query arguments that become null.
    
    for (int i = q.args.size() - 1; i >= 0; i--) {

      Qry q_i_before = q.args.get(i);
      Qry q_i_after = optimizeQuery (q_i_before);

      if (q_i_after == null) {
        q.removeArg(i);			// optimization deleted the argument
      } else {
        if (q_i_before != q_i_after) {
          q.args.set (i, q_i_after);	// optimization changed the argument
        }
      }
    }

    //  If the operator now has no arguments, it is deleted.

    if (q.args.size () == 0) {
      return null;
    }

    //  Only SCORE operators can have a single argument.  Other
    //  query operators that have just one argument are deleted.

    if ((q.args.size() == 1) &&
        (! (q instanceof QrySopScore))) {
      q = q.args.get (0);
    }

    return q;

  }

  /**
   * Return a query tree that corresponds to the query.
   * 
   * @param qString
   *          A string containing a query.
   * @param qTree
   *          A query tree
   * @throws IOException Error accessing the Lucene index.
   */
  static Qry parseQuery(String qString, RetrievalModel model) throws IOException {

    //  Add a default query operator to every query. This is a tiny
    //  bit of inefficiency, but it allows other code to assume
    //  that the query will return document ids and scores.

    String defaultOp = model.defaultQrySopName ();
    qString = defaultOp + "(" + qString + ")";

    //  Simple query tokenization.  Terms like "near-death" are handled later.

    StringTokenizer tokens = new StringTokenizer(qString, "\t\n\r ,()/", true);
    String token = null, last_token = null;

    //  This is a simple, stack-based parser.  These variables record
    //  the parser's state.
    
    Qry currentOp = null;
    Stack<Qry> opStack = new Stack<Qry>();
    ArrayList<ArrayList<Double>> weights = new ArrayList<ArrayList<Double>>();
    ArrayList<Integer> curr_count = new ArrayList<Integer>();
    int current_stack_position=-1;
    
    //  Each pass of the loop processes one token. The query operator
    //  on the top of the opStack is also stored in currentOp to
    //  make the code more readable.

    while (tokens.hasMoreTokens()) {

      token = tokens.nextToken();
      //System.out.println(" token = " + token);

      if (token.matches("[ ,(\t\n\r]")) {
        continue;
      }else if(token.matches("-?\\d+(\\.\\d+)?") && (current_stack_position>=0) && 
    		  (curr_count.get(current_stack_position)%2==0)
    		  && ( (currentOp instanceof QrySopWand) || (currentOp instanceof QrySopWsum))){

    		  double temp_num = Double.parseDouble(token);
    		  weights.get(current_stack_position).add(temp_num);
    		  //System.out.println(" insert weight : curr stack " + current_stack_position + " num = " + temp_num );
    		  curr_count.set(current_stack_position, curr_count.get(current_stack_position)+1);
 		  
       }else if (token.equals(")")) {	// Finish current query op.

        // If the current query operator is not an argument to another
        // query operator (i.e., the opStack is empty when the current
        // query operator is removed), we're done (assuming correct
        // syntax - see below).

        opStack.pop();

        if (opStack.empty())
          break;

	// Not done yet.  Add the current operator as an argument to
        // the higher-level operator, and shift processing back to the
        // higher-level operator.

        if(currentOp instanceof QrySopWand){
        	int temp_size = weights.get(current_stack_position).size();
        	
        	for(int i=0;i<temp_size;i++)
        		((QrySopWand) currentOp).weights.add(weights.get(current_stack_position).get(i));
        	
        	//System.out.println("Wand weight deletion: curr stack  = " + current_stack_position + 
        	//		              " size = "  + weights.get(current_stack_position).size());
        	curr_count.set(current_stack_position,0);
        	weights.get(current_stack_position--).clear();
        	
        }else if(currentOp instanceof QrySopWsum){
        	int temp_size = weights.get(current_stack_position).size();
        	
        	for(int i=0;i<temp_size;i++)
        		((QrySopWsum) currentOp).weights.add(weights.get(current_stack_position).get(i));
        	
        	curr_count.set(current_stack_position,0);
        	weights.get(current_stack_position--).clear();
        }
        
        Qry arg = currentOp;
        currentOp = opStack.peek();
	    currentOp.appendArg(arg);
	    
      } else if (token.equalsIgnoreCase("#or")) {
    	  if( (currentOp instanceof QrySopWand) || (currentOp instanceof QrySopWsum)){
    		  curr_count.set(current_stack_position, curr_count.get(current_stack_position)+1);
    	  }
        currentOp = new QrySopOr ();
        currentOp.setDisplayName (token);
        opStack.push(currentOp);
      } else if (token.equalsIgnoreCase("#syn")) {
    	  if( (currentOp instanceof QrySopWand) || (currentOp instanceof QrySopWsum)){
    		  curr_count.set(current_stack_position, curr_count.get(current_stack_position)+1);
    	  }
        currentOp = new QryIopSyn();
        currentOp.setDisplayName (token);
        opStack.push(currentOp);
      } else if(token.equalsIgnoreCase("#and")){
    	  if( (currentOp instanceof QrySopWand) || (currentOp instanceof QrySopWsum)){
    		  curr_count.set(current_stack_position, curr_count.get(current_stack_position)+1);
    	  }
    	currentOp = new QrySopAnd();
        currentOp.setDisplayName (token);
        opStack.push(currentOp);  
      }else if(token.equalsIgnoreCase("#sum")){
    	  if( (currentOp instanceof QrySopWand) || (currentOp instanceof QrySopWsum)){
    		  curr_count.set(current_stack_position, curr_count.get(current_stack_position)+1);
    	  }
    	currentOp = new QrySopSum();
        currentOp.setDisplayName (token);
        opStack.push(currentOp);  
      }else if(token.equalsIgnoreCase("#wand")){
    	  if( (currentOp instanceof QrySopWand) || (currentOp instanceof QrySopWsum)){
    		  curr_count.set(current_stack_position, curr_count.get(current_stack_position)+1);
    	  }
      	currentOp = new QrySopWand();
        currentOp.setDisplayName (token);
        opStack.push(currentOp); 
        
        current_stack_position++;
        if(weights.size() <= current_stack_position){
        	weights.add(new ArrayList<Double>());
        }
        curr_count.add(0);
        
      }else if(token.equalsIgnoreCase("#wsum")){
    	  if( (currentOp instanceof QrySopWand) || (currentOp instanceof QrySopWsum)){
    		  curr_count.set(current_stack_position, curr_count.get(current_stack_position)+1);
    	  }
          	currentOp = new QrySopWsum();
            currentOp.setDisplayName (token);
            opStack.push(currentOp); 
            
            /* Weights array increment */
            current_stack_position++;
            if(weights.size() <= current_stack_position){
            	weights.add(new ArrayList<Double>());
            }
            curr_count.add(0);
            
        }else if(token.equalsIgnoreCase("#near") || 
    		  token.equalsIgnoreCase("#window")
    		  ){
    	  
    	if(token.equalsIgnoreCase("#near"))
    		currentOp = new QryIopNear();
    	else 
    		currentOp = new QryIopWindow();
    	
        currentOp.setDisplayName (token); 
        opStack.push(currentOp);
        
        String prev_token = token;
        // Get the near distance and check for errors : 
        if(tokens.hasMoreTokens()){
            
        	token = tokens.nextToken();
        	if(token.equals("/")){
        		
        		if(tokens.hasMoreTokens()){
        				token = tokens.nextToken();
        				int dis = Integer.parseInt(token);
        				
        				if(prev_token.equalsIgnoreCase("#near")){
        					((QryIop)currentOp).nearDis = dis; 
        				}else if(prev_token.equalsIgnoreCase("#window")){
        					((QryIop)currentOp).WindowDis = dis;
        				}
        		}else{
        			throw new IllegalArgumentException
                    ("Error:  Query syntax is incorrect. Near operater has no '/' " + qString);
        		}
        		
        	}else{
        		throw new IllegalArgumentException
                ("Error:  Query syntax is incorrect. Near operater has no '/' " + qString);
        	}
        }else{
        	throw new IllegalArgumentException
            ("Error:  Query syntax is incorrect" + qString);
        }        
        
      }else{

    	  // 
    	  if((currentOp instanceof QrySopWsum) || (currentOp instanceof QrySopWand)){
    		  curr_count.set(current_stack_position, curr_count.get(current_stack_position) +1);
    	  }
        //  Split the token into a term and a field.
        int delimiter = token.indexOf('.');
        String field = null;
        String term = null;

        if (delimiter < 0) {
          field = "body";
          term = token;
        } else {
          field = token.substring(delimiter + 1).toLowerCase();
          term = token.substring(0, delimiter);
        }

        if ((field.compareTo("url") != 0) &&
	    (field.compareTo("keywords") != 0) &&
	    (field.compareTo("title") != 0) &&
	    (field.compareTo("body") != 0) &&
            (field.compareTo("inlink") != 0)) {
             System.out.println(" Exceptional term " + term);
             term = term + "." + field;
             field = "body";
         	//throw new IllegalArgumentException ("Error: Unknown field " + token);
        }

        //  Lexical processing, stopwords, stemming.  A loop is used
        //  just in case a term (e.g., "near-death") gets tokenized into
        //  multiple terms (e.g., "near" and "death").

        String t[] = tokenizeQuery(term);
        
        for (int j = 0; j < t.length; j++) {
          Qry termOp = new QryIopTerm(t [j], field);
	      currentOp.appendArg (termOp);
	    }
        
        if(t.length==0){
        	
        	if((currentOp instanceof QrySopWsum)
        			|| ((currentOp instanceof QrySopWand))){
        	if(weights.size()>0){
        		weights.remove(weights.size()-1);
        	}
           }
        }
        
      }
      
      last_token = token;
    }

    
    //  A broken structured query can leave unprocessed tokens on the opStack,
    if (tokens.hasMoreTokens()) {
      throw new IllegalArgumentException
        ("Error:  Query syntax is incorrect.  " + qString);
    }

    return currentOp;
  }

  /**
   * Print a message indicating the amount of memory used. The caller
   * can indicate whether garbage collection should be performed,
   * which slows the program but reduces memory usage.
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

    Qry q = parseQuery(qString, model);
    q = optimizeQuery (q);

    // Show the query that is evaluated
    // System.out.println("    --> " + q);
    
    if (q != null) {

      ScoreList r = new ScoreList ();
      
      if (q.args.size () > 0) {		// Ignore empty queries

        q.initialize (model);
        
        while (q.docIteratorHasMatch (model)) {
          int docid = q.docIteratorGetMatch ();
          double score = ((QrySop) q).getScore (model);
          r.add (docid, score);
          q.docIteratorAdvancePast (docid);
        }
      }
      
      r.sort();
      
      int count=0;
      ScoreList ret = new ScoreList ();
      while(count<Math.min(100, r.size())){
    	  ret.add(r.getDocid(count), r.getDocidScore(count));
    	  count++;
      }
      
      return ret;
      
    } else
      return null;
  }

  /**
   * Process the query file.
   * @param queryFilePath
   * @param model
   * @throws IOException Error accessing the Lucene index.
   */
  static void processQueryFile(Map<String, String> parameters,
                               RetrievalModel model)
      throws IOException {

	String queryFilePath = parameters.get("queryFilePath");
    BufferedReader input = null;
    String[] originalQueryTokens;
    String originalQuery = " ";

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

        printMemoryUsage(true);

        String qid = qLine.substring(0, d);
        CurrQueryID = qid;
        String query = qLine.substring(d + 1);
        exact_original_query = query;
        String extended_query;

        // System.out.println("Query " + qLine);

        ScoreList r = null;

        if((model instanceof RetrievalModelIndri) && parameters.containsKey("fb")
        		&& (parameters.get("fb").equals("true"))){
        	
        	originalQueryTokens = tokenizeQuery(query);
        	//convert the tokens into the one string query
        	originalQuery =" ";
        	for(int i=0;i<originalQueryTokens.length;i++){
        		originalQuery = originalQuery.concat(originalQueryTokens[i]);
        		originalQuery = originalQuery.concat(" ");
        	}
        	
        	extended_query  = expandedQuery(qid,originalQuery,parameters,model);
        	originalQuery =" ";
      	    String reformedExpandedQuery;
     	    reformedExpandedQuery =  " #WAND ( " + extended_query + " )";
        	r = processQuery(reformedExpandedQuery,model);
        	
        }else{
         r = processQuery(query, model);
        }
        
        if (r != null) {
          printResults(parameters,qid, r);
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
  static void printResults(Map<String,String> parameters,
		  String queryName, ScoreList result) throws IOException {

    String QryResult = new String();
    File file = new File(parameters.get("trecEvalOutputPath"));

	if (!file.exists()) {
		file.createNewFile();
	}

	FileWriter fw = new FileWriter(file.getAbsoluteFile(),true);
	BufferedWriter bw = new BufferedWriter(fw);
	
	for (int i = 0; i < result.size(); i++) {
		QryResult = queryName + " Q0 " + Idx.getExternalDocid(result.getDocid(i)) + " " + (new Integer(i+1)).toString()
				+ " " + (new Double(result.getDocidScore(i))).toString() + " run-1 ";
		//System.out.println(QryResult);
		bw.write(QryResult);
		bw.newLine();
		QryResult = "";
      }
	
	if(result.size()==0){
		QryResult = queryName +	" Q0	dummy	1	0	run-1";
		//System.out.println(QryResult);
		bw.write(QryResult);
		bw.newLine();
	}
	
	bw.close();
  }
  
  static void printExpandedQuery(Map<String,String> parameters,
		  String queryName, ScoreList result) throws IOException {

    String QryResult = new String();
    File file = new File(parameters.get("fbExpansionQueryFile"));

	if (!file.exists()) {
		file.createNewFile();
	}

	FileWriter fw = new FileWriter(file.getAbsoluteFile(),true);
	BufferedWriter bw = new BufferedWriter(fw);
	
	for (int i = 0; i < result.size(); i++) {
		QryResult = queryName + " Q0 " + Idx.getExternalDocid(result.getDocid(i)) + " " + (new Integer(i+1)).toString()
				+ " " + (new Double(result.getDocidScore(i))).toString() + " run-1 ";
		System.out.println(QryResult);
		bw.write(QryResult);
		bw.newLine();
		QryResult = "";
      }
	
	if(result.size()==0){
		QryResult = queryName +	" Q0	dummy	1	0	run-1";
		//System.out.println(QryResult);
		bw.write(QryResult);
		bw.newLine();
	}
	
	bw.close();
  }

  /**
   * Read the specified parameter file, and confirm that the required
   * parameters are present.  The parameters are returned in a
   * HashMap.  The caller (or its minions) are responsible for
   * processing them.
   * @return The parameters, in <key, value> format.
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
      String[] pair = line.split ("=");
      parameters.put(pair[0].trim(), pair[1].trim());
    } while (scan.hasNext());
    
    scan.close();

    if (! (parameters.containsKey ("indexPath") &&
           parameters.containsKey ("queryFilePath") &&
           parameters.containsKey ("trecEvalOutputPath") &&
           parameters.containsKey ("retrievalAlgorithm"))) {
      throw new IllegalArgumentException
        ("Required parameters were missing from the parameter file.");
    }
    
    return parameters;
  }

  /**
   * Given a query string, returns the terms one at a time with stopwords
   * removed and the terms stemmed using the Krovetz stemmer.
   * 
   * Use this method to process raw query terms.
   * 
   * @param query
   *          String containing query
   * @return Array of query tokens
   * @throws IOException Error accessing the Lucene index.
   */
  static String[] tokenizeQuery(String query) throws IOException {

    TokenStreamComponents comp =
      ANALYZER.createComponents("dummy", new StringReader(query));
    TokenStream tokenStream = comp.getTokenStream();

    CharTermAttribute charTermAttribute =
      tokenStream.addAttribute(CharTermAttribute.class);
    tokenStream.reset();

    List<String> tokens = new ArrayList<String>();

    while (tokenStream.incrementToken()) {
      String term = charTermAttribute.toString();
      tokens.add(term);
    }

    return tokens.toArray (new String[tokens.size()]);
  }
  
  /**
   * Given a original query string, returns the expanded query from the 
   * top documents.
   * 
   * Use this method if fb parameter in Indri model is given as true.
   * 
   * @param query
   *          String containing query
   * @return String : Expanded query
   * @throws IOException Error accessing the Lucene index.
   */
  static String expandedQuery(String qid,String orignalQuery,Map<String, String> parameters,
		  RetrievalModel model){
	  
	  /* Just a double check on fb parameter */
	  if(!((parameters.containsKey("fb") && (parameters.get("fb").equals("true"))))){
		  System.out.println("QryExp: Incorrect Indri params for extended query");
		  return null;
	  }
	  
	  /* Variable to store the parameters, docs id and the results */
	  String expanded_query = null;
	  int max_docs =0;
	  LinkedHashMap<Integer,Double> topDocs = new LinkedHashMap<Integer,Double>();
	  LinkedHashMap<String,Double> topWords = new LinkedHashMap<String,Double>();
	  ScoreList list = null;
	  
	  max_docs = Integer.parseInt(parameters.get("fbDocs"));
	  
	  //System.out.println("QryExp : max docs = "+ max_docs + " max_terms = " + max_terms); 

	  /* If the Initial result file is already provided, then use it else 
	     first get the results from the initial query */
	  if(parameters.containsKey("fbInitialRankingFile")){
		  //System.out.println("QryExp : initial file provided with name = " + parameters.get("fbInitialRankingFile"));
		  topDocs = extractTopDocs(qid,max_docs,parameters);
	  }else{ 
		  try {
			list = processQuery(orignalQuery,model);
		} catch (IOException e) {
			e.printStackTrace();
		}
		  topDocs = extractTopDocsfromScoreList(max_docs,list);
	  }
	  
	  /* Extract the top words from topdocs along with the weights */
	  topWords = extractTopWords(parameters,topDocs);
	  expanded_query = formNewQuery(model,topWords,parameters);
	  return expanded_query;
  }
  
  /* If the initial result file is not provided, then get the score list from the query 
   * and use the sorted score list to get top docs which would be used for query expansion
   */
  private static LinkedHashMap<Integer,Double> extractTopDocsfromScoreList(int max_docs, ScoreList list) {
	
	  int size = list.size(), idx=0;
	  LinkedHashMap<Integer,Double> TopDocs = new LinkedHashMap<Integer,Double>();
	  
	  for(idx=0;idx<Math.min(size,max_docs);idx++){
		  TopDocs.put(list.getDocid(idx),list.getDocidScore(idx));
	  }
	  
	  return TopDocs;
  }

/* Read the initial ranking file provided by through input parameters 
 * and extract top docs with the scores. */
  static LinkedHashMap<Integer,Double> extractTopDocs(String qid,int max_docs, Map<String, String> parameters){
	 
	  String file_path = parameters.get("fbInitialRankingFile");
	  int start_idx=0, line_len=0, idx=0,space_count=0,internalDocID,line_count=0;
	  double docScore=0.0;
	  String qLine = null, externalDocString=null,externalDocScore=null,queryid=null;
	  //Data Structure to store the final doc Id(internal) and the score 
	  LinkedHashMap<Integer,Double> externalDocList = new LinkedHashMap<Integer,Double>();
	  
	  BufferedReader InitialRanking = null;
	  
	  try{
		  InitialRanking = new BufferedReader(new FileReader(file_path));
		   
		  /* For each line, retrieve the docId(external) */
		    while ((qLine = InitialRanking.readLine()) != null) {
		    	
		    	if(line_count>=max_docs)
		    		break;
		    		
		    	line_len = qLine.length();
		    	space_count=0;
		    	for(idx=0;idx<line_len;idx++){
		    		
		    		if(qLine.charAt(idx) == ' '){
		    			space_count++;
		    			
		    			if(space_count==1){
		    				queryid = qLine.substring(0,idx);
		    				if(qid.equals(queryid)==false){
		    					break;
		    				}else{
		    					line_count++;
		    				}
		    			}else if(space_count==2){
		    				start_idx = idx+1;
		    			}else if(space_count==3){
		    		    	externalDocString = qLine.substring(start_idx,idx);
		    		    	start_idx = idx+1; // Start idx of the score
		    			}else if(space_count==4){
		    				start_idx=idx+1;
		    			}else if(space_count==5){
		    				externalDocScore = qLine.substring(start_idx,idx);
		    				internalDocID = Idx.getInternalDocid(externalDocString);
		    				docScore = Double.parseDouble(externalDocScore);
		    				externalDocList.put(internalDocID,docScore);
		    				break;
		    			}
		    		}else{
		    			continue;
		    		}
		    	}
		    }
		    
	  } catch (Exception e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	} 
	 
	//System.out.println("QryExp : Done with doc extraction, Doc Size = " + externalDocList.size());
	  
	return externalDocList;  
  }
  
  /* This function will get the top terms with the weights to be used in the query 
   * expansion */
  static LinkedHashMap<String,Double> extractTopWords(Map<String, String> parameters, 
		  LinkedHashMap<Integer,Double> topDocs){
	
	//System.out.println("QryExp : ExtractTopWords ");
	int max_terms = Integer.parseInt(parameters.get("fbTerms"));
	int max_docs = Integer.parseInt(parameters.get("fbDocs"));
	int doc_size= Math.min(max_docs,topDocs.size()), stem_length,stem_freq=0,word_count=0,docIdx=0;
	String field ="body",wordString;
	Map<String,ArrayList<Integer>> bagOfWords = new LinkedHashMap<String,ArrayList<Integer>>();
	Map<String,Long> netStemFreq = new HashMap<String,Long>();
	Map<String,Double> FinalScore = new LinkedHashMap<String,Double>();
	Map<Integer,Integer> DocMapping = new LinkedHashMap<Integer,Integer>();
	LinkedHashMap<String,Double> SortedMap = new LinkedHashMap<String,Double>();
	TermVector tv;
	double docScore=0.0, mau = Double.parseDouble(parameters.get("fbMu"));
  	double doc_length,mle=0.0,total_score=0.0,temp_score=0.0;
	ArrayList<Integer> tempArrayList = new ArrayList<Integer>();

  	// First create a bag of words(with zero score) from all the docs
    Iterator<Entry<Integer, Double>> itr = topDocs.entrySet().iterator();
	   while (itr.hasNext() && docIdx<doc_size) {
		   
	       Entry<Integer, Double> entry = itr.next();
	       Integer key = entry.getKey();
	       docScore = entry.getValue();
	       
	       DocMapping.put(docIdx, key);
	       
			try {
				
				tv = new TermVector(key , field);
				stem_length = tv.stemsLength();
				//System.out.println(" doc idx = " + docIdx + " stem_len = " + stem_length);
				
				for(int stemIdx=0;stemIdx<stem_length;stemIdx++){

					wordString = tv.stemString(stemIdx);
					stem_freq = tv.stemFreq(stemIdx);
					
					if((stem_freq<=0) || (wordString.indexOf('.')>=0) || (wordString.indexOf(',')>=0)){
						continue;
					}
									
					if(bagOfWords.containsKey(wordString)==false){
						bagOfWords.put(wordString,new ArrayList<Integer>());
						tempArrayList = bagOfWords.get(wordString);
						for (int i = 0; i < doc_size; i++) {
							  tempArrayList.add(0);
							}
						netStemFreq.put(wordString,tv.totalStemFreq(stemIdx));
					}
					
					tempArrayList = bagOfWords.get(wordString);
					tempArrayList.add(docIdx,stem_freq);
					bagOfWords.put(wordString,tempArrayList);
					
					//System.out.println(" stem freq from bagOfWords = " +  (bagOfWords.get(wordString)).get(docIdx));
				}
				
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			docIdx++;
	   }
	   
	   /* Each words go through each doc and the score is calculated across all docs 
	    * and add them*/
	    Iterator<Entry<String, ArrayList<Integer>>> Worditr = bagOfWords.entrySet().iterator();
		double tot_len=0.0;
		try {
			tot_len = (double)Idx.getSumOfFieldLengths(field);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		
		while (Worditr.hasNext()) {
			   
		   Entry<String, ArrayList<Integer>> entry = Worditr.next();  
		   String presentWord = entry.getKey();
		   ArrayList<Integer> wordList = entry.getValue();
		   long totalStemFreq = netStemFreq.get(presentWord);
		   
		  // System.out.println(" Word = " + presentWord + " totStemFreq = " + totalStemFreq);
		   total_score =0.0;

		   try{
		   
			   for(int doc_idx=0;doc_idx<doc_size;doc_idx++){
			   
				   int docId = DocMapping.get(doc_idx);
				   doc_length = (double)Idx.getFieldLength(field,docId);
				   stem_freq = wordList.get(doc_idx);
				   mle= totalStemFreq/tot_len;
				   docScore = topDocs.get(docId);
			   
				   temp_score = (stem_freq + mau*(mle))/(doc_length + mau);
				   temp_score = temp_score*docScore*Math.log(tot_len/totalStemFreq);
			       
				   total_score += temp_score;
			   }
			   
		  }catch(Exception e){
			  e.printStackTrace();
		  }
		   
		   FinalScore.put(presentWord, total_score);
	   }

        TreeMap<String, Double> sorted = new TreeMap<String, Double>(new ValueComparator((HashMap<String, Double>) FinalScore));
        sorted.putAll(FinalScore);
        
        for (Entry<String, Double> pair: sorted.entrySet()) {
            SortedMap.put(pair.getKey(), pair.getValue());
            
            if(++word_count >= max_terms){
            	break;
            }
        }
        
        return SortedMap;
  }
  
  /* This function will take the most frequent words already retrieved and form the 
   * new expanded query based on the original query, parameters provided 
   */
   static String formNewQuery(RetrievalModel model,LinkedHashMap<String,Double>topWords,
		   Map<String, String> parameters){
	  
	   //System.out.println("Form new query :");
	   String expandedQuery = " ",first_part=" ",w_string=" ",fileQry=" ";
	   double w =  Double.parseDouble(parameters.get("fbOrigWeight"));
	   
	   w_string = Double.toString(w);
	   
	   first_part = w_string + " #and (" + exact_original_query + " ) ";

	   System.out.println(" first_part = " + first_part);
	   // Form the 2nd part of the final query i.e. Expanded one
	   Iterator<Entry<String, Double>> exp_itr = topWords.entrySet().iterator();
	   
	   while (exp_itr.hasNext()) {
	       Entry<String, Double> entry = exp_itr.next();

	       double value = entry.getValue();
		   DecimalFormat df = new DecimalFormat("#.####");
	       expandedQuery= expandedQuery.concat(df.format(value));
	       expandedQuery= expandedQuery.concat(" ");
	      
	       expandedQuery=expandedQuery.concat(entry.getKey());
	       expandedQuery=expandedQuery.concat(" "); 
	   }
	   
	   expandedQuery = " #Wand (" + expandedQuery + " ) ";
	   fileQry = CurrQueryID + " : "  + expandedQuery;
	   
	   System.out.println("Expansion = " + fileQry);
	   // Write the expanded query to the path provided in the parameter file/map
	   File file = new File(parameters.get("fbExpansionQueryFile"));

	   FileWriter fw = null;
		try {
			
			if (!file.exists()){
				file.createNewFile();
			}
			fw = new FileWriter(file.getAbsoluteFile(),true);
			BufferedWriter bw = new BufferedWriter(fw);
			bw.write(fileQry);
			bw.close();
			
		} catch (IOException e) {
			e.printStackTrace();
		}

	   w_string = Double.toString(1-w);
	   expandedQuery =  first_part + " "+ w_string + expandedQuery;

	   System.out.println("QryExp: Final Exapnded query " + expandedQuery);   
	   return expandedQuery;
  }
}
