package org.reactome.qa.doisuggester;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.schema.InvalidAttributeException;

/**
 * An object that makes a DOI suggestion about an RLE
 * @author sshorser
 *
 */
public class Suggester
{
	/**
	 * Represents a suggestion about a single pathway, and the RLEs and InstanceEdits that are related to the suggestion.
	 * @author sshorser
	 *
	 */
	class Suggestion
	{
		private String pathway;
		private Map<String, Set<String>> rlesToInstanceEdits;

		/**
		 * @return the pathway.
		 */
		public String getPathway()
		{
			return pathway;
		}

		/**
		 * Sets the pathway.
		 * @param pathway
		 */
		public void setPathway(String pathway)
		{
			this.pathway = pathway;
		}

		/**
		 * @return a map that is a mapping of RLEs to a set of InstanceEdits.
		 */
		public Map<String, Set<String>> getRlesToInstanceEdits()
		{
			return rlesToInstanceEdits;
		}

		/**
		 * Sets a map that is a mapping of RLEs to a set of InstanceEdits.
		 * @param rlesToInstanceEdits
		 */
		public void setRlesToInstanceEdits(Map<String, Set<String>> rlesToInstanceEdits)
		{
			this.rlesToInstanceEdits = rlesToInstanceEdits;
		}
	}

	private ReactionlikeEvent rle;

	/**
	 * Create a new suggester.
	 * @param reactionlikeEvent The RLE to make a suggestion about.
	 */
	public Suggester(ReactionlikeEvent reactionlikeEvent)
	{
		this.rle = reactionlikeEvent;
	}

	/**
	 * Gets a suggestion. The suggestion is actually returned as a set. In the case where
	 * an RLE has multiple parents and both may require a new DOI, then > 1 Suggestion object will be returned.
	 * @return A set of Suggestion objects.
	 */
	public Set<Suggestion> getSuggestion()
	{
		Set<Suggestion> suggestions = new HashSet<>();
		Set<GKInstance> pathways = new HashSet<>();
		Set<String> suggestedInstanceEdits = new HashSet<>();
		// Handle RLEs that exist in previous versions of Reactome.
		if (!this.rle.isNewRLE())
		{
			List<GKInstance> newInstanceEdits = Suggester.filterForNonReactomeInstanceEdits(this.rle.getNewInstanceEdits());
			// IF an RLE has "new" InstanceEdits, we may have to make a DOI suggestion.
			if (!newInstanceEdits.isEmpty())
			{
				pathways.addAll(getPathways(newInstanceEdits));
				newInstanceEdits.parallelStream().forEach( ie -> suggestedInstanceEdits.add(ie.toString()) );
			}
		}
		// Handle RLEs that are new to this release.
		else
		{
			List<GKInstance> instanceEdits = this.rle.getAuthoredInstanceEdits();
			instanceEdits.addAll(this.rle.getReviewedInstanceEdits());
			instanceEdits.addAll(this.rle.getRevisedInstanceEdits());
			List<GKInstance> nonReactomeInstanceEdits = Suggester.filterForNonReactomeInstanceEdits(instanceEdits);
			nonReactomeInstanceEdits.parallelStream().forEach( ie -> suggestedInstanceEdits.add(ie.toString()) );
			pathways.addAll(getPathways(nonReactomeInstanceEdits));
		}


		if (!pathways.isEmpty())
		{
			for (GKInstance pathway : pathways)
			{
				Suggestion s = new Suggestion();
				s.setPathway(pathway.toString());
				Map<String, Set<String>> rleMapToInstanceEdits = new HashMap<>();
				rleMapToInstanceEdits.put(this.rle.getUnderlyingInstance().toString(), suggestedInstanceEdits);
				s.setRlesToInstanceEdits(rleMapToInstanceEdits);
				suggestions.add(s);
			}
		}
		return suggestions;
	}

	/**
	 * Gets a set of pathways for this suggester's RLE that do not have "new" InstanceEdits.
	 * @param newInstanceEdits
	 * @return a set of Pathways
	 */
	private Set<GKInstance> getPathways(List<GKInstance> newInstanceEdits)
	{
		Set<GKInstance> pathways = new HashSet<>();
		for (GKInstance newInstanceEdit : newInstanceEdits)
		{
			Set<GKInstance> ancestors = this.rle.getFurthestAncestorsWithoutNewInstanceEdit(newInstanceEdit);
			if (ancestors != null)
			{
				pathways.addAll(ancestors);
			}
		}
		return pathways;
	}

	/**
	 * Filters a list of InstanceEdits such that only "non-Reactome" InstanceEdits are returned.
	 * And InstanceEdits is considered to be "non-Reactome" if at least one author of the InstanceEdit is not
	 * a member of the Reactome project.
	 * @param instanceEdits
	 * @return A list of InstanceEdits that are "non-Reactome". It will be a subset of instanceEdits.
	 */
	private static List<GKInstance> filterForNonReactomeInstanceEdits(List<GKInstance> instanceEdits)
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
}
