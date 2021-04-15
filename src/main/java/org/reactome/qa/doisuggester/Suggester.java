package org.reactome.qa.doisuggester;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gk.model.GKInstance;

/**
 * Makes a DOI suggestion about an RLE
 * @author sshorser
 *
 */
public class Suggester
{

	public class Suggestion
	{
		private String pathway;
		private Map<String, Set<String>> rlesToInstanceEdits;

		public String getPathway()
		{
			return pathway;
		}
		public void setPathway(String pathway)
		{
			this.pathway = pathway;
		}
		public Map<String, Set<String>> getRlesToInstanceEdits()
		{
			return rlesToInstanceEdits;
		}
		public void setRlesToInstanceEdits(Map<String, Set<String>> rlesToInstanceEdits)
		{
			this.rlesToInstanceEdits = rlesToInstanceEdits;
		}
	}	
	private ReactionlikeEvent rle;
	
	/**
	 * Create a new suggester.
	 * @param doiSuggesterRle The RLE to make a suggestion about.
	 */
	public Suggester(ReactionlikeEvent doiSuggesterRle)
	{
		this.rle = doiSuggesterRle;
	}
	
	/**
	 * Gets the suggestion.
	 * @return
	 */
	public Set<Suggestion> getSuggestion()
	{
//		String suggestion = null;
		Set<Suggestion> suggestions = new HashSet<>();
		Set<GKInstance> pathways = new HashSet<>();
		Set<String> suggestedInstanceEdits = new HashSet<>();
		// Handle RLEs that exist in previous versions of Reactome.
		if (!this.rle.isNewRLE())
		{
			List<GKInstance> newInstanceEdits = ReactionlikeEvent.filterForNonReactomeInstanceEdits(this.rle.getNewInstanceEdits());
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
			List<GKInstance> nonReactomeInstanceEdits = ReactionlikeEvent.filterForNonReactomeInstanceEdits(instanceEdits);
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
}
