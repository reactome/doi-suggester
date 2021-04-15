package org.reactome.qa.doisuggester;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;

/**
 * Represents a Reactionlike Event, in the context of DOI suggestion activities.
 * @author sshorser
 *
 */
public class ReactionlikeEvent
{
	// The underlying RLE, in the current database.
	private GKInstance rle;
	// Corresponding RLE from the previous Release's database.
	private ReactionlikeEvent prevRle = null;
	// The DB Adaptor
//	private MySQLAdaptor adaptor;
	
	private List<GKInstance> authoredInstancedEdits;
	private List<GKInstance> reviewedInstancedEdits;
	private List<GKInstance> revisedInstancedEdits;
	
	private Set<GKInstance> ancestors = new HashSet<>();
	
//	private static Comparator<? super GKInstance> instanceEditComparator = new Comparator<GKInstance>()
//		{
//			@Override
//			public int compare(GKInstance instanceEdit, GKInstance other) throws RuntimeException
//			{
//				try
//				{
//					String instanceEditDateTime = (String) instanceEdit.getAttributeValue(ReactomeJavaConstants.dateTime);
//					// not all date strings have milliseconds and this cauess problems when trying to format the
//					// strings, so easier to just remove the millisecond parts.
//					instanceEditDateTime = instanceEditDateTime.replaceAll("\\.[0-9]+$", "");
//					String otherDateTime = (String) instanceEdit.getAttributeValue(ReactomeJavaConstants.dateTime);
//					otherDateTime = otherDateTime.replaceAll("\\.[0-9]+$", "");
//					// When dates are converted to strings in the Reactome API, they are formatted like this: "2015-06-12 04:00:00.0"
//					DateTimeFormatter sdf = DateTimeFormatter.ofPattern("y-M-d H:m:s");
//					LocalDateTime ldt = LocalDateTime.parse(instanceEditDateTime, sdf);
//					LocalDateTime odt = LocalDateTime.parse(otherDateTime, sdf);
//					
//					return ldt.compareTo(odt);
//				}
//				catch (Exception e)
//				{
//					e.printStackTrace();
//					// Throw a RuntimeException because we are overriding compare from Comparator which is not
//					// declared as throwing an exception. But if an exception is raised (probably by attempts
//					// to get data from the database), then there is no sensible way to continue comparing
//					// two objects so the exception must be rethrown.
//					throw new RuntimeException(e);
//				}
//			}
//		};
//	
	/**
	 * Constructor to create a DOISuggester-contexted Reaction-like Event object.
	 * @param instance - The underlying instance from the database. This is a ReactionlikeEvent object.
	 * @param prevAdaptor - A database adaptor that should point to the *previous* version of the database. This will be used to look at previous versions of the <code>instance</code>
	 * @throws Exception
	 */
	public ReactionlikeEvent(GKInstance instance, MySQLAdaptor prevAdaptor) throws Exception
	{
		this.rle = instance;
//		this.adaptor = (MySQLAdaptor) this.rle.getDbAdaptor();
		GKInstance prevInstance = prevAdaptor.fetchInstance(this.rle.getDBID());
		if (prevInstance != null)
		{
			ReactionlikeEvent prevDOISuggesterRLE = new ReactionlikeEvent(prevInstance);
			this.prevRle = prevDOISuggesterRLE;
		}
	}
	
	/**
	 * Returns true if the underlying RLE instance is new - there is no corresponding instance in the previous Releases's database.
	 * @return
	 */
	public boolean isNewRLE()
	{
		return this.prevRle == null;
	}
	
	/**
	 * A constructor that should be used to create an instance of this class that represents a version of a ReactionlikeEvent object
	 * from a previous database, or a parent from the current database, because it does not take a MySQLAdaptor for a previous database as an argument.
	 * @param instance - The underlying instance from the database.
	 * @throws Exception
	 */
	public ReactionlikeEvent(GKInstance instance) throws Exception
	{
		this.rle = instance;
//		this.adaptor = (MySQLAdaptor) this.rle.getDbAdaptor();
	}
	
	public List<GKInstance> getAuthoredInstanceEdits()
	{
		if (this.authoredInstancedEdits == null)
		{
			try
			{
				this.authoredInstancedEdits = this.getSortedInstanceEdits(ReactomeJavaConstants.authored);
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
		return this.authoredInstancedEdits;
	}
	
	public List<GKInstance> getReviewedInstanceEdits()
	{
		if (this.reviewedInstancedEdits == null)
		{
			try
			{
				this.reviewedInstancedEdits = this.getSortedInstanceEdits(ReactomeJavaConstants.reviewed);
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
		return this.reviewedInstancedEdits;
	}

	
	
	public List<GKInstance> getRevisedInstanceEdits()
	{
		if (this.revisedInstancedEdits == null)
		{
			try
			{
				this.revisedInstancedEdits = this.getSortedInstanceEdits(ReactomeJavaConstants.revised);
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
		return this.revisedInstancedEdits;
	}
	
	public GKInstance getUnderlyingInstance()
	{
		return this.rle;
	}
	
	private List<GKInstance> getSortedInstanceEdits(String attributeName) throws InvalidAttributeException, Exception
	{
		List<GKInstance> instanceEdits = this.rle.getAttributeValuesList(attributeName);
//		instanceEdits.sort(DOISuggesterRLE.instanceEditComparator);
		return instanceEdits;
	}
	
	public List<GKInstance> getNewInstanceEdits()
	{
		List<GKInstance> newInstanceEdits = new ArrayList<>();
		if (this.prevRle != null)
		{
			// check the authored Instance Edits
			List<GKInstance> prevAuthoredIEs = this.prevRle.getAuthoredInstanceEdits();
			newInstanceEdits.addAll(this.getNewInstanceEditsFromList(this.getAuthoredInstanceEdits(), prevAuthoredIEs));
			
			// check the reviewed Instance Edits
			List<GKInstance> prevReviewedIEs = this.prevRle.getReviewedInstanceEdits();
			newInstanceEdits.addAll(this.getNewInstanceEditsFromList(this.getReviewedInstanceEdits(), prevReviewedIEs));
			
			// check the reviewed Instance Edits
			List<GKInstance> prevReviseddIEs = this.prevRle.getRevisedInstanceEdits();
			newInstanceEdits.addAll(this.getNewInstanceEditsFromList(this.getRevisedInstanceEdits(), prevReviseddIEs));
		}
		
		return newInstanceEdits;
	}

	private List<GKInstance> getNewInstanceEditsFromList(List<GKInstance> currentInstanceEdits, List<GKInstance> prevInstanceEdits)
	{
		List<GKInstance> newInstanceEdits = new ArrayList<>();
		// A naive approach to this problem would simply look at list sizes, but that wont' work...
		//
		// Normally, you'd think that the list of InstanceEdits from the current list will *always* contain the 
		// same InstanceEdits as the list from the previous Release as well as any new ones - but Joel pointed out that sometimes InstanceEdits
		// in previous versions have gotten lost due to bugs/data issues, so comparing the previous list to the current list
		// could result in the previous list having *fewer* items. So, you need to examine the elements - you can not use list size alone.  
		// 
		// We need to go through ALL of the InstanceEdits in the current database's list and compare them to ALL of the InstanceEdits 
		// in the list from the previous database to determine if it's truly new.
		for (GKInstance instanceEdit : currentInstanceEdits)
		{
			int i = 0;
			boolean found = false;
			while (!found && i < prevInstanceEdits.size())
			{
				found = instanceEdit.getDBID().equals(prevInstanceEdits.get(i).getDBID());
				i++;
			}
			// if we went through ALL previous InstanceEdits and found == false, then instanceEdit is *new*!
			if (!found)
			{
				newInstanceEdits.add(instanceEdit);
			}
		}
		return newInstanceEdits;
	}
	
	public static List<GKInstance> filterForNonReactomeInstanceEdits(List<GKInstance> instanceEdits)
	{
		List<GKInstance> nonReactomeInstanceEdits = new ArrayList<>();
		
		try
		{
			// For an InstanceEdit to be considered non-Reactome, there must exist at least one author whose project is 
			// not "Reactome"
			for (GKInstance instanceEdit : instanceEdits)
			{
				List<GKInstance> authors = instanceEdit.getAttributeValuesList(ReactomeJavaConstants.author);
				if (authors != null && !authors.isEmpty())
				{
					int i = 0;
					boolean isNonReactome = false;
					while (!isNonReactome && i < authors.size())
					{
						GKInstance author = authors.get(i);
						// TODO: Notify Guanming that "project" is not in ReactomeJavaConstants.
						String project = (String) author.getAttributeValue("project");
						if (project == null || project.trim().equals("") || !project.trim().equalsIgnoreCase("reactome") )
						{
							isNonReactome = true;
							nonReactomeInstanceEdits.add(instanceEdit);
						}
						i++;
					}
				}
//				else
//				{
//					// Log a warning about an InstanceEdit with NO authors - there's probably some data cleanup that needs to happen.
//				}
			}
		}
		catch (InvalidAttributeException e)
		{
			e.printStackTrace();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return nonReactomeInstanceEdits;
	}
	
	/**
	 * Gets the furthest ancestor of this Reaction-like event which does not have newInstanceEdit as one of its InstanceEdits.
	 * @param newInstanceEdit a "new" InstanceEdit.
	 * @return The pathway object.
	 */
	public Set<GKInstance> getFurthestAncestorsWithoutNewInstanceEdit(GKInstance newInstanceEdit)
	{
		List<GKInstance> parents;
		try
		{
			parents = (List<GKInstance>) this.rle.getReferers(ReactomeJavaConstants.hasEvent);
			if (parents != null)
			{
				for (GKInstance parent : parents)
				{
					this.ancestors.addAll(this.getFurthestAncestorWithoutNewInstanceEdit(parent, newInstanceEdit));
				}
			}
		}
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return this.ancestors;
	}
	
	/**
	 * Gets the ancestor of a pathway/RLE that is furthest up the tree and also does not have the same InstanceEdit.
	 * @param a parent pathway
	 * @param newInstanceEdit the InstanceEdit to check for.
	 * @return
	 */
	private Set<GKInstance> getFurthestAncestorWithoutNewInstanceEdit(GKInstance pathway, GKInstance newInstanceEdit)
	{
//		GKInstance pathToReturn = null;
		try
		{
			List<GKInstance> parents = (List<GKInstance>) pathway.getReferers(ReactomeJavaConstants.hasEvent);
			if (parents == null || parents.isEmpty() )
			{
				// If we somehow get to the root level where there are no more parents,
				// just return this pathway. Probably not going to happen, but it's
				// probably a good idea to have this base case anyway.
//				pathToReturn =  pathway;
			}
//			else if (parents.size() > 1)
//			{
				// This should not happen - an RLE should only be referred to via 1 "hasEvent" attribute. But, 
				// if it does happen log it and exit - this is too weird to handle in code.
//				System.out.println("Too many ancestors (" + parents.size() + ") for object: " + this.rle.toString());
//				System.exit(-1);
//			}
			else
			{
				// There are a few ReactionlikeEvents that are referred to by multiple Pathways via hasEvent...
				// For example, R-HSA-349426 ("Phosphorylation of MDM4 by CHEK2") is in both "Stabilization of p53"
				// under the top-level pathway Cell Cycle, and "Regulation of TP53 Degradation" under the top-level
				// pathway Gene Expression.
				// Both of these parents must be recursively checked. 
				for (GKInstance parent : parents)
				{
					List<GKInstance> parentInstanceEdits = new ArrayList<>();
					// Ok, technically, the parent object should be a Pathway, and a ReactionelikeEvent
					// is *not* a sublass of Pathway, but they are both subclasses of Event,
					// and Event has the attributes "authored", "revised", "reviewed", so should still work.
					ReactionlikeEvent parentPathway = new ReactionlikeEvent(parent);
					parentInstanceEdits.addAll(parentPathway.getAuthoredInstanceEdits());
					parentInstanceEdits.addAll(parentPathway.getReviewedInstanceEdits());
					parentInstanceEdits.addAll(parentPathway.getRevisedInstanceEdits());
					boolean instanceEditFound = false;
					int i = 0;
					while (!instanceEditFound && i < parentInstanceEdits.size())
					{
						GKInstance parentInstanceEdit = parentInstanceEdits.get(i);
						if (newInstanceEdit.getDBID().equals(parentInstanceEdit.getDBID()))
						{
							instanceEditFound = true;
							this.ancestors.addAll(this.getFurthestAncestorWithoutNewInstanceEdit(parent, newInstanceEdit));
						}
						i++;
					}
					// If an ancestor of the RLE has the same InstanceEdit, it means we need to go up one more level to find an ancestor 
					// that does *not* have the InstanceEdit
//					if (instanceEditFound)
//					{
						
//						this.ancestors.add(pathToReturn);
//					}
					if (!instanceEditFound)
					{
						// in this case we found a path that should be returned, but because an RLE could have had > 1 parent, we will
						// ALSO add directly to the set.
//						pathToReturn = parent;
						this.ancestors.add(parent);
					}
				}
			}
		}
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// 
		return this.ancestors;
	}
}