import java.io.*;
import java.util.ArrayList;

/**
 *  The NEAR operator for all retrieval models.
 */
public class QryIopNear extends QryIop {

	 /**
	   *  Evaluate the query operator; the result is an internal inverted
	   *  list that may be accessed via the internal iterators.
	   *  @throws IOException Error accessing the Lucene index.
	   */
	@Override
	protected void evaluate() throws IOException {

		// Initialize the parameters and create an array list(postings)
		int near_dis = this.nearDis,match_count=0, prev_pos=-1,args_size=this.args.size();
		ArrayList<Integer> PosList = new ArrayList<Integer>();
		this.invertedList = new InvList (this.getField());

		if (this.args.size() == 0) {
		      return;
		}
		
		// HasMatchAll will bring check if for the DocID which is common to all the term
		// If there is no match of doc(common docID), then no need to proceed further.
		while(this.docIteratorHasMatchAll(null)){
			
			prev_pos = -1; // this is to initialized the position of previous Query term
			match_count = 1; // Total number of consecutive matches that has been done

			// Go through all the term and check for the respective positions limit w.r.t prev term
			for(int i=0;i<args_size;i++){
				
				QryIop q_i = (QryIop) args.get(i);
				
				if(!(q_i.locIteratorHasMatch()))
					break;
					
					// This is the first term, just initialize the prev_pos and move to next term
					if(prev_pos==-1){
						prev_pos = q_i.locIteratorGetMatch();
						continue;	
					}
							
					//Keep on incrementing the present term location till it crossed prev_pos
					while((prev_pos > q_i.locIteratorGetMatch())){
						q_i.InclocIterator();
						if(!(q_i.locIteratorHasMatch())){
							break;
						}
					}		
					
					// loc iterator has reached the end, skip to next common docID.
					if(!(q_i.locIteratorHasMatch())){
							break;
						}
					
					// Location of this term is more than near_dis, so skip it
					// Also, move the location iterator of 1st term to next.
					if((q_i.locIteratorGetMatch() - prev_pos) > near_dis){
						match_count=1;prev_pos = i = -1;
						((QryIop)this.args.get(0)).InclocIterator();
						continue;
					}
					
					// Location iterator has position within near_dis.
					prev_pos = q_i.locIteratorGetMatch();
					match_count++;
					
					//Number of matches reached the args size i.e. all term matched
					if(match_count==args_size){
									
						int j=0;match_count=1; // Prepare for fresh search
						PosList.add(prev_pos); // Add the position to the positing list.
						prev_pos = i=-1;
						
						// Increment the location position of all the term by 1 since one 
						// location can only be used once in counting the term freq.
						for(j=0;j<args_size;j++){
							
							QryIop next_q = (QryIop) args.get(j);
										
							next_q.InclocIterator();
							if(!(next_q.locIteratorHasMatch())){
									break;
								}
							}
							// If any term location reached the end, then
							if(j!=args_size){
								break;
							}
						}
					}
			
			// Copy the position list to the inverted list
			if(PosList.size()>0){
				this.invertedList.appendPosting(this.args.get(0).docIteratorGetMatch(),PosList); 
				PosList.clear();
			}
			
			// Increment the doc idx of 1st doc by 1 else we might end in infinite loop.
			if(this.args.get(0).docIteratorHasMatch(null)){ 
				this.args.get(0).docIteratorAdvancePast(this.args.get(0).docIteratorGetMatch());
			}
		}
		
		// Keep the Iterator back to the initial position
		if(this.invertedList.df >0 ){
			this.setdocIterator();
			this.setlocIterator();
		}
	}
}