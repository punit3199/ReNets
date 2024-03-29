import java.util.*;
public class Coordinator<T extends Comparable<T>> {
	public int threshold=Driver.threshold;
	public int nodes=Driver.nodes;
	public int tot_workingSet_size=0;
	public int max_help=Driver.max_help;
	public Circ_list<T> helper_nodes=new Circ_list<>();
	public HashMap<T,Node<T>> map=new HashMap<>();         //map containing all the nodes in network
	public Splay_node<T> temp;
	public void initialize(T[] ar)                         //this function take as input an array of keys from Driver and initialize nodes with these keys and then add it to the map 
	{
		for(int i=1;i<ar.length;i++)
		{
			Node<T> n=new Node<>();
			n.key=ar[i];
			map.put(ar[i],n);
			helper_nodes.insert(ar[i]);                   //initially all small nodes are helper nodes.
		}
	}
	public void check(T x,T y)                            //this function just takes the src key and dst key as input and then sends it to process request.
	{
		Node<T> src=map.get(x);
	    Node<T> dst=map.get(y);
	    process_request(src,dst);
	    if(src.working_set.size()>threshold)
	    	src.ego_tree.printTree();
	    System.out.println();
	}
	public void process_request(Node<T> src,Node<T> dst)  //this function decides whether to answer the communication request or add a new route
	{
		double start=System.nanoTime();
		if(src.status==0)                                 //src node is small
		{
			if(dst.status==0)                             //dst node is small
			{
				if(!src.S.containsKey(dst))                               //if dst not present is S, then send for add route
				{
					System.out.println("Adding Route between "+src.key+"(small)"+" "+dst.key+"(small)");
					addRoute(src,dst);
				}
				else
					System.out.println("Direct connection from "+src.key+" to "+dst.key+" with 1 hop");
			}
			else                                         //dst node is large
			{
				if(src.L.containsKey(dst.ego_tree))
				{
					int h=parent_traversal(src.L.get(dst.ego_tree).parent);
					System.out.println("Reached from "+src.key+" to root in "+h+" hops and then directly to "+dst.key+" in 1 hop");
				}
				else                                      //if dst ego tree not present is L, then send for add route
				{
					System.out.println("Adding Route between "+src.key+"(small)"+" "+dst.key+"(large)");
					addRoute(src,dst);
				}
			}
		}
		else                                            //src node is large
		{
			if(dst.status==0)                           //dst node is small
			{
				src.ego_tree.root.host=null;
				src.ego_tree.search(dst.key);
				if(src.ego_tree.root.key==dst.key)
					System.out.println("Destination found in "+src.ego_tree.hops+" hops");
				else
				{
					System.out.println("Adding Route between "+src.key+"(large)"+" "+dst.key+"(small)");
					addRoute(src,dst);
				}
				src.ego_tree.root.host=src; //important: since the root will change after splaying, the host pointer must be given to this new root
			}
			else                                       //dst node is large
			{
				//find helper node
				src.ego_tree.root.host=null;
//				dst.ego_tree.root.host=null;
				temp=null;
				Splay_node<T> helper=src.ego_tree.search(dst.key); //whether it is unsuccessful search or not should it always splay.
				if(helper.key!=dst.key)
				{
					System.out.println("Adding Route between "+src.key+"(large)"+" "+dst.key+"(large)");
					addRoute(src,dst);
				}
				else {
				int g=parent_traversal(helper.relay.parent);
				src.ego_tree.splay(helper);
				src.ego_tree.root.host=src; 
				dst.ego_tree.splay(helper.relay);
				dst.ego_tree.root.host=dst; 
				System.out.println("Found the helper node( "+helper.represent+" ) and jumped to another tree in 1 hop and from there to root in "+g+" hops");}
			}
			double end=System.nanoTime();
			double time_taken=end-start;
			System.out.println(time_taken);          
		}
	}
	public int parent_traversal(Splay_node<T> node)
	{
		if(node==null)
			return 0;
		return 1+parent_traversal(node.parent);
	}
	public void addRoute(Node<T> src,Node<T> dst)                   //function for adding routes according to different cases(small-small,small-large,large-small,large,-large)
	{
		tot_workingSet_size+=2;
//		if(tot_workingSet_size>nodes*threshold/2)
//		{
//			reset();
//		}
		int bool1=0;                                                //bool1 and bool2 are just to check whether the nodes have become large after makelarge or they were large initially
		int bool2=0;                                                //because if they have become large after being send to makelarge, then all the additions and replaying is done there itself.
		src.working_set.add(dst); 
		if(src.status==0 && src.working_set.size()>threshold)       //adding node to working set and then according to degree sending the node to makelarge function.    
		{
			bool1++;
			makeLarge(src);
		}
		dst.working_set.add(src);
		if(dst.status==0 && dst.working_set.size()>threshold)
		{
			bool2++;
			makeLarge(dst);
		}
		//making the connection between the nodes
		if(src.status==0 && dst.status==0)                          //small-small case: just adding the nodes to each others small nodes list(S)
		{
			src.S.put(dst,1);
			dst.S.put(src,1);
		}
		else if(bool2==0 && src.status==0 && dst.status==1)        //small-large case: adding the src in the ego tree of dst
		{
			Splay_node<T> n=dst.ego_tree.insert(src.key);
			dst.ego_tree.splay(n);
		}
		else if(bool1==0 && src.status==1 && dst.status==0)        //large-small case: adding the dst in the ego tree of src
		{
			Splay_node<T> n=src.ego_tree.insert(dst.key);
			src.ego_tree.splay(n);
		}
		else if(bool1==0 && bool2==0 && src.status==1 && dst.status==1)   //large-large case: find a suitable helper node and replay between src ego_tree and dst ego_tree
		{
			Splay_node<T>[]arr=find_helper_node(src,dst);
			src.ego_tree.splay(arr[0]);
			dst.ego_tree.splay(arr[1]);
		}
	}
	public void makeLarge(Node<T> n)
	{
		n.status=1;
		n.L.clear();
		n.S.clear();
		splayTree<T> ego_tree=new splayTree<>();
		int x=0;
		ArrayList<Node<T>> large=new ArrayList<>();
		helper_nodes.remove(n.key);
		for(int i=0;i<=n.working_set.size()-1;i++)     //starting to add the keys from working set and also performing splaying operation(to kind of maintain balances tree)
		{
			//only adding small nodes to the tree
			if(n.working_set.get(i).status==0 && x==0)
			{
				ego_tree.set_root(n.working_set.get(i).key); 
				x++;
			}
			else if(n.working_set.get(i).status==0 && x!=0)
			{
				Splay_node<T> h=ego_tree.insert(n.working_set.get(i).key);
				ego_tree.splay(h);
			}
			else if(n.working_set.get(i).status==1)
			{
				n.working_set.get(i).ego_tree.root.host=null;
				//just storing the large nodes, also remove this key from all the ego trees in which it was when it was small
				n.working_set.get(i).ego_tree.delete(n.key);
				n.working_set.get(i).ego_tree.root.host=n.working_set.get(i);
				large.add(n.working_set.get(i));                                        //storing all large nodes in a list
			}
		}
		n.ego_tree=ego_tree;
		n.ego_tree.root.host=n;
		for(int i=0;i<large.size();i++)
		{
			Splay_node<T>[] arr=find_helper_node(n,large.get(i));
			if(arr==null)                                                              //this means the network is reset
				return;
		}
	}        //**** update the working set after finding helper node
	public Splay_node<T>[] find_helper_node(Node<T> node1,Node<T> node2)
	{
		if(helper_nodes.size==0)                                                        //if there are no helper nodes, then reset the network
		{
			reset();
			return null;
		}
		T helper=helper_nodes.get();
		System.out.println(node1.key+" "+node2.key+" "+helper);
		map.get(helper).num_helps++;
		if(map.get(helper).num_helps==max_help)
			helper_nodes.remove(helper);
		Splay_node<T> tr1=node1.ego_tree.insert(node2.key);
		Splay_node<T> tr2=node2.ego_tree.insert(node1.key);
		tr1.represent=helper;
		tr2.represent=helper;
		tr1.relay=tr2;
		tr2.relay=tr1;
		Splay_node<T>[] arr=new Splay_node[2];
		arr[0]=tr1;
		arr[1]=tr2;
		return arr;
	}
	public void inorder(Splay_node<T> node,ArrayList<Splay_node<T>> ar)
	{
		if(node==null)
			return;
		inorder(node.left,ar);
		ar.add(node);
		inorder(node.right,ar);
	}
	public void reset()
	{
		System.out.println("Network reset");
		for(int i=1;i<=nodes;i++)
		{
			Node<T> n=map.get(i);
			n.L.clear();
			n.S.clear();
			n.working_set.clear();
		}
	}
}