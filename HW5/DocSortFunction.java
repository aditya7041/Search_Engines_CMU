/** 
 *  Copyright (c) 2016, Carnegie Mellon University.  All Rights Reserved.
 */
import java.util.*;

//a comparator that compares Strings
class DocSortFunction implements Comparator<Document>{

	ArrayList<Document> docList = new ArrayList<Document>();

	public DocSortFunction( ArrayList<Document> d ){
		docList = d;
	}

	@Override
	public int compare(Document d1, Document d2) {
		
		int q1 = Integer.parseInt(d1.qid);
		int q2 = Integer.parseInt(d2.qid);
		
		if(q1 < q2){
			return -1;
		}else if(q1 > q2){
			return 1;
		}else{
			
			if(d1.score>d2.score){
				return -1;
			}else{
				return 1;
			}
			
		}
	}
}