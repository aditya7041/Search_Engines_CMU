import java.io.*;
import java.util.ArrayList;

/**
 *  The NEAR operator for all retrieval models.
 */
public class QryIopWindow extends QryIop {

	 /**
	   *  Evaluate the query operator; the result is an internal inverted
	   *  list that may be accessed via the internal iterators.
	   *  @throws IOException Error accessing the Lucene index.
	   */
	@Override
	protected void evaluate() throws IOException {

		// Initialize the parameters and create an array list(postings)
		int win_dis = this.WindowDis,itr=0,start_args=-1,
				args_size=this.args.size(),win_start=-1,win_stop=-1,present_loc=0;
		
		ArrayList<Integer> PosList = new ArrayList<Integer>();
		this.invertedList = new InvList (this.getField());
		
		// HasMatchAll will bring check if for the DocID which is common to all the term
		// If there is no match of doc(common docID), then no need to proceed further.
		while(this.docIteratorHasMatchAll(null)){
			
			/* Get all the position per doc */
			while(true){
				
				win_start = Integer.MAX_VALUE;
				win_stop = Integer.MIN_VALUE;
				
				for(itr=0;itr<args_size;itr++){
				
					QryIop q_i = (QryIop) args.get(itr);
				
					if(!(q_i.locIteratorHasMatch()))
						break;
				
					present_loc = q_i.locIteratorGetMatch();
				
					if(present_loc <win_start){
						win_start = present_loc;
						start_args = itr;
					}
					if(present_loc>win_stop){
						win_stop = present_loc;
					}
				
				}
			
			/* One of the term has reached the end, so break from this doc*/
			if(itr!=args_size)
				break;
			
			/* Add the current window end position to the list 
			 * Increment the loc iterator of all by one, since current position has been used
			 * */
			if(win_stop-win_start+1 <= win_dis){
				
				PosList.add(win_stop);
			
				for(int i=0;i<args_size;i++)
					((QryIop) args.get(i)).InclocIterator();
			
			}else{ 
				/* It is not within the window, increment the lowest idx */
				int dis = win_stop-win_start+1;
				((QryIop) args.get(start_args)).InclocIterator();
			}
			
		  }/* While(1) */
			
		    /* Now, add this doc and all the collected window position to the inverted list */
			if(PosList.size()>0){
				this.invertedList.appendPosting(this.args.get(0).docIteratorGetMatch(),PosList); 
				PosList.clear();
			}
			
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