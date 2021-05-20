package org.reactome.qa.doisuggester;

import java.util.ArrayList;
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

	private List<GKInstance> authoredInstancedEdits;
	private List<GKInstance> reviewedInstancedEdits;
	private List<GKInstance> revisedInstancedEdits;

	private Set<GKInstance> ancestors = new HashSet<>();

	/**
	 * Constructor to create a DOISuggester-contexted Reaction-like Event object.
	 * @param instance - The underlying instance from the database. This is a ReactionlikeEvent object.
	 * @param prevAdaptor - A database adaptor that should point to the *previous* version of the database. This will be used to look at
	 * previous versions of the <code>instance</code>
	 * @throws Exception if anything happens while fetching an instance from the database.
	 */
	public ReactionlikeEvent(GKInstance instance, MySQLAdaptor prevAdaptor) throws Exception
	{
		this.rle = instance;
		GKInstance prevInstance = prevAdaptor.fetchInstance(this.rle.getDBID());
		if (prevInstance != null)
		{
			ReactionlikeEvent prevDOISuggesterRLE = new ReactionlikeEvent(prevInstance);
			this.prevRle = prevDOISuggesterRLE;
		}
	}

	/**
	 * Is this Reaction-like event "new"?
	 * @return True if the underlying RLE instance is new - there is no corresponding instance in the previous Releases's database.
	 */
	public boolean isNewRLE()
	{
		return this.prevRle == null;
	}

	/**
	 * A constructor that should be used to create an instance of this class that represents a version of a ReactionlikeEvent object
	 * from a previous database, or a parent of a ReactionlikeEvent from the current database, because it does not take a MySQLAdaptor
	 * for a previous database as an argument.
	 * @param instance - The underlying instance from the database.
	 */
	public ReactionlikeEvent(GKInstance instance)
	{
		this.rle = instance;
	}

	/**
	 * @return a list of "authored" InstanceEdits for this RLE
	 */
	public List<GKInstance> getAuthoredInstanceEdits()
	{
		if (this.authoredInstancedEdits == null)
		{
			try
			{
				this.authoredInstancedEdits = this.rle.getAttributeValuesList(ReactomeJavaConstants.authored);
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
		return this.authoredInstancedEdits;
	}

	/**
	 * @return A list of "reviewed" InstanceEdits for this RLE
	 */
	public List<GKInstance> getReviewedInstanceEdits()
	{
		if (this.reviewedInstancedEdits == null)
		{
			try
			{
				this.reviewedInstancedEdits = this.rle.getAttributeValuesList(ReactomeJavaConstants.reviewed);
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
		return this.reviewedInstancedEdits;
	}

	/**
	 * @return A list of "revised" InstanceEdits for this RLE
	 */
	public List<GKInstance> getRevisedInstanceEdits()
	{
		if (this.revisedInstancedEdits == null)
		{
			try
			{
				this.revisedInstancedEdits = this.rle.getAttributeValuesList(ReactomeJavaConstants.revised);
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
		return this.revisedInstancedEdits;
	}

	/**
	 * @return The underlying GKInstance object to which this ReactionlikeEvent object is a wrapper.
	 */
	public GKInstance getUnderlyingInstance()
	{
		return this.rle;
	}

	/**
	 * Gets "new" InstanceEdits. An InstanceEdit is considered new if it exists in the current version of this RLE, but does *not*
	 * exist in the version of this RLE from the previous release. The InstanceEdits could be of these three types: authored, revised, reviewed.
	 * @return A list of "new" InstanceEdits.
	 */
	public List<GKInstance> getNewInstanceEdits()
	{
		List<GKInstance> newInstanceEdits = new ArrayList<>();
		if (this.prevRle != null)
		{
			// check the authored Instance Edits
			List<GKInstance> prevAuthoredIEs = this.prevRle.getAuthoredInstanceEdits();
			newInstanceEdits.addAll(this.getNewInstanceEditsFromLists(this.getAuthoredInstanceEdits(), prevAuthoredIEs));

			// check the reviewed Instance Edits
			List<GKInstance> prevReviewedIEs = this.prevRle.getReviewedInstanceEdits();
			newInstanceEdits.addAll(this.getNewInstanceEditsFromLists(this.getReviewedInstanceEdits(), prevReviewedIEs));

			// check the reviewed Instance Edits
			List<GKInstance> prevReviseddIEs = this.prevRle.getRevisedInstanceEdits();
			newInstanceEdits.addAll(this.getNewInstanceEditsFromLists(this.getRevisedInstanceEdits(), prevReviseddIEs));
		}

		return newInstanceEdits;
	}

	/**
	 * Gets InstanceEdits that are associated with the current version of an object but not with the previous version
	 * of an object.
	 * @param currentInstanceEdits - A list of InstanceEdits from the current database
	 * @param prevInstanceEdits - a list of InstanceEdits from the previous database.
	 * @return Objects in currentInstanceEdits that are not in prevInstanceEdits
	 */
	private List<GKInstance> getNewInstanceEditsFromLists(List<GKInstance> currentInstanceEdits, List<GKInstance> prevInstanceEdits)
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

	/**
	 * Gets the furthest ancestors of this Reaction-like event which does not have newInstanceEdit as one of its InstanceEdits.
	 * This method will perform a recursive search from a reaction-like event up to a top-level pathway, searching for a pathway that
	 * does not have a "new" InstanceEdit. A set of all such pathways is returned.
	 * @param newInstanceEdit a "new" InstanceEdit.
	 * @return The pathway objects.
	 */
	public Set<GKInstance> getFurthestAncestorsWithoutNewInstanceEdit(GKInstance newInstanceEdit)
	{
		try
		{
			this.ancestors.addAll(this.getFurthestAncestorWithoutNewInstanceEdit(this.getUnderlyingInstance(), newInstanceEdit));
		}
		catch (Exception e)
		{
			// TODO log/handle this properly in future PR
			e.printStackTrace();
		}
		return this.ancestors;
	}

	/**
	 * Private recursive method to search up the Pathway hierarchy.
	 * This method gets the ancestors of a pathway/RLE that is furthest up the tree and also does not have the same InstanceEdit.
	 * @param event A pathway or RLE to examine. Remember: Event is a parent-class to ReactionlikeEvent and Pathway.
	 * @param newInstanceEdit the InstanceEdit to check for.
	 * @return The pathway objects.
	 */
	private Set<GKInstance> getFurthestAncestorWithoutNewInstanceEdit(GKInstance event, GKInstance newInstanceEdit)
	{
		try
		{
			List<GKInstance> parents = (List<GKInstance>) event.getReferers(ReactomeJavaConstants.hasEvent);
			// Only proceed if there are valid parents. If there are no valid parents, then don't do anything because
			// this process only makes sense if pathway has parents.
			if (! (parents == null || parents.isEmpty()) )
			{
				// There are a few ReactionlikeEvents that are referred to by multiple Pathways via hasEvent...
				// For example, R-HSA-349426 ("Phosphorylation of MDM4 by CHEK2") is in both "Stabilization of p53"
				// under the top-level pathway Cell Cycle, and "Regulation of TP53 Degradation" under the top-level
				// pathway Gene Expression.
				// Both of these parents must be recursively checked.
				for (GKInstance parent : parents)
				{
					List<GKInstance> parentInstanceEdits = new ArrayList<>();
					// Ok, technically, the parent object should be a Pathway, and a ReactionlikeEvent
					// is *not* a subclass of Pathway, but they are both subclasses of Event,
					// and Event has the attributes "authored", "revised", "reviewed", so should work.
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
							// If an ancestor of the RLE has the same InstanceEdit, it means we need to go up one more level to find an ancestor
							// that does *not* have the InstanceEdit
							this.ancestors.addAll(this.getFurthestAncestorWithoutNewInstanceEdit(parent, newInstanceEdit));
						}
						i++;
					}
					if (!instanceEditFound)
					{
						// A pathway that does not have the "new" instanceEdit is the sort of pathway we're interested in
						// so add it to the list of ancestors that will be returned.
						// If pathway != this.rle, add pathway, otherwise, add parent.
						if (event.getDBID().equals(this.rle.getDBID()))
						{
							this.ancestors.add(parent);
						}
						else
						{
							this.ancestors.add(event);
						}
					}
				}
			}
		}
		catch (Exception e)
		{
			// event.getReferers(ReactomeJavaConstants.hasEvent) is probably the only thing that could throw an exception,
			// but the underlying API throws Exception rather than using more specific custom exceptions...
			// TODO: Handle this better with proper logging and maybe re-throwing? For a future PR.
			e.printStackTrace();
		}

		return this.ancestors;
	}
}
