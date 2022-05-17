package org.reactome.qa.doisuggester;

import org.gk.model.ReactomeJavaConstants;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.*;
import java.util.Map.Entry;

import org.gk.model.GKInstance;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;
import org.reactome.util.general.MandatoryProperties;
import org.reactome.util.general.MandatoryProperties.PropertyHasNoValueException;
import org.reactome.util.general.MandatoryProperties.PropertyNotPresentException;

public class Main
{
	public static void main(String[] args) throws FileNotFoundException, IOException
	{
		System.out.println("Checking for RLEs that might need a DOI...");
		String pathToConfig = Paths.get("src", "main", "resources", "config.properties").toString();
		String username;
		String password;
		String database;
		String databasePrev;
		String host;
		int port;
		try(FileInputStream fis = new FileInputStream(pathToConfig))
		{
			MandatoryProperties props = new MandatoryProperties();
			props.load(fis);

			username = props.getMandatoryProperty("automatedDOIs.user");
			password = props.getMandatoryProperty("automatedDOIs.password");
			database = props.getMandatoryProperty("automatedDOIs.dbName");
			databasePrev = props.getMandatoryProperty("automatedDOIs.prevDbName");
			host = props.getMandatoryProperty("automatedDOIs.host");
			port = Integer.valueOf(props.getMandatoryProperty("automatedDOIs.port"));
			MySQLAdaptor dbAdaptor = new MySQLAdaptor(host, database, username, password, port);
			MySQLAdaptor dbAdaptorPrev = new MySQLAdaptor(host, databasePrev, username, password, port);

			Set<GKInstance> newRLEs = new HashSet<>();
			Map<GKInstance, Set<Map<GKInstance, GKInstance>>> pathway2Reactions2NewIEs = new HashMap<>();

			// Iterate through all ReactionlikeEvents (RlEs) in the 'current' test_reactome
			Collection<GKInstance> RLEs = (Collection<GKInstance>) dbAdaptor.fetchInstancesByClass(ReactomeJavaConstants.ReactionlikeEvent);
			System.out.println(RLEs.size() + " Reaction-like events will be checked.");
			for (GKInstance currentRLE : RLEs)
			{

				// Only check RlEs that are not found in the 'inferredFrom' of any instances.
				if (currentRLE.getReferers(ReactomeJavaConstants.inferredFrom) == null)
				{
					GKInstance previousRLE = dbAdaptorPrev.fetchInstance(currentRLE.getDBID());
					// If no previous version of the RLE, add it to the New RLE list (to be processed later).
					if (previousRLE == null)
					{
						// An RLE is "new" if there is no equivalent RLE in the previous database AND if it is referred to by a "hasEvent" attribute of another object. 
						if (currentRLE.getReferers(ReactomeJavaConstants.hasEvent) != null)
						{
							newRLEs.add(currentRLE);
						}
						// New RLEs have their own logic below, so skip the current loop that deals with
						// existing RlEs.
//						continue;
					}
					else // process RLEs that have prior versions.
					{
						pathway2Reactions2NewIEs = processPreexistingRLEs(pathway2Reactions2NewIEs, currentRLE, previousRLE);
					}
					
				}
			}

			Map<GKInstance, Set<Map<GKInstance, GKInstance>>> pathwaysMap = processNewRLEs(newRLEs, pathway2Reactions2NewIEs);

			printOutput(pathwaysMap);

		}
		catch (PropertyHasNoValueException | PropertyNotPresentException e)
		{
			System.err.println("Your properties file appears to be misconfigured: " + e.getMessage());
			e.printStackTrace();
			System.exit(-1);
		}
		catch (SQLException e)
		{
			e.printStackTrace();
		}
		catch (InvalidAttributeException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private static Map<GKInstance, Set<Map<GKInstance, GKInstance>>> processPreexistingRLEs(Map<GKInstance, Set<Map<GKInstance, GKInstance>>> pathway2Reactions2NewIEs, GKInstance currentRLE, GKInstance previousRLE) throws InvalidAttributeException, Exception
	{
		Map<GKInstance, Set<Map<GKInstance, GKInstance>>> pathwaysMap = pathway2Reactions2NewIEs;
		// Get all reviewed, authored, and revised values from the current and previous
		// version of the RlE.
		List<GKInstance> currentRLEReviewedInstances = currentRLE.getAttributeValuesList(ReactomeJavaConstants.reviewed);
		List<GKInstance> currentRLEAuthoredInstances = currentRLE.getAttributeValuesList(ReactomeJavaConstants.authored);
		List<GKInstance> currentRLERevisedInstances = currentRLE.getAttributeValuesList(ReactomeJavaConstants.revised);
		List<GKInstance> previousRLEReviewedInstances = previousRLE.getAttributeValuesList(ReactomeJavaConstants.reviewed);
		List<GKInstance> previousRLEAuthoredInstances = previousRLE.getAttributeValuesList(ReactomeJavaConstants.authored);
		List<GKInstance> previousRLERevisedInstances = previousRLE.getAttributeValuesList(ReactomeJavaConstants.revised);

		// Check if any of those attributes have a new instance edit
		if (hasNewInstanceEdit(currentRLEReviewedInstances, previousRLEReviewedInstances)
			|| hasNewInstanceEdit(currentRLEAuthoredInstances, previousRLEAuthoredInstances)
			|| hasNewInstanceEdit(currentRLERevisedInstances, previousRLERevisedInstances))
		{

			// Filter out any instance edits (IE) created by someone from Reactome.
			// This is because Pathways are only officially considered updated once they are
			// reviewed by an external person.
			List<GKInstance> newInstanceEdits = getNewNonReactomeInstanceEdits(currentRLE, currentRLEReviewedInstances, currentRLEAuthoredInstances, currentRLERevisedInstances, previousRLEReviewedInstances, previousRLEAuthoredInstances, currentRLERevisedInstances);

			if (!newInstanceEdits.isEmpty())
			{
				// Now it will check the immediate parent Pathway of the RlE to see if the new
				// IE exists there.
				// If it is not found, it will continue to check up the Pathway hierarchy to see
				// if a new IE exists anywhere.

				// Note: Expand so that instances with multiple parents are handled.
				GKInstance parentPathway = (GKInstance) currentRLE.getReferers(ReactomeJavaConstants.hasEvent).iterator().next();
				List<GKInstance> parentPathwayReviewedAuthoredRevisedInstances = parentPathway.getAttributeValuesList(ReactomeJavaConstants.reviewed);
				parentPathwayReviewedAuthoredRevisedInstances.addAll(parentPathway.getAttributeValuesList(ReactomeJavaConstants.authored));
				parentPathwayReviewedAuthoredRevisedInstances.addAll(parentPathway.getAttributeValuesList(ReactomeJavaConstants.revised));

				for (GKInstance newIE : newInstanceEdits)
				{
					for (GKInstance parentPathwayIE : parentPathwayReviewedAuthoredRevisedInstances)
					{

						// At time of writing, no 'existing' RlEs have a new IE in the immediate parent
						// when using
						// 'test_slice_20210118' as the current DB, and 'slice_previous_as_74' as the
						// previous DB.
						if (hasRLEInstanceEdit(newIE, parentPathwayIE))
						{
							System.out.println("This IE exists in immediate parent Pathway");
						}
						else
						{

							// Since the new IE wasn't found in the immediate parent, check up the Pathway
							// hierarchy.
							List<GKInstance> parentPathwaysWithRLEInstanceEdit = getGrandparentPathwayWithRLEInstanceEdit(parentPathway, newIE);

							for (GKInstance parentPathwayWithRLEInstanceEdit : parentPathwaysWithRLEInstanceEdit) {
								// If a Pathway with the IE was found, it is added to a map organized as
								// {pathway => {reaction:newIE}}
								if (parentPathwayWithRLEInstanceEdit != null) {
									addPathwayToMap(pathwaysMap, currentRLE, newIE, parentPathwayWithRLEInstanceEdit);
								} else {
									addPathwayToMap(pathwaysMap, currentRLE, newIE, parentPathway);
								}
							}
						}
					}
				}
			}
		}
		return pathwaysMap;
	}

	private static Map<GKInstance, Set<Map<GKInstance, GKInstance>>> processNewRLEs(Set<GKInstance> newRLEs, Map<GKInstance, Set<Map<GKInstance, GKInstance>>> pathway2Reactions2NewIEs) throws Exception
	{
		
		Map<GKInstance, Set<Map<GKInstance, GKInstance>>> pathwaysMap = pathway2Reactions2NewIEs;
		
		// For cases where this the first Release that a RlE has appeared (ie. fetching
		// for the RlE in the previous DB returned null).

		// As before, iterate through all RlEs.
		for (GKInstance newRLE : newRLEs)
		{
			// Get non-Reactome IEs from the revised, authored, and reviewed attributes.
			List<GKInstance> newRLEInstanceEdits = getNonReactomeEventIEs(newRLE);

			// Look through all parents of the RlE, trying to find the new instance edit in
			// any of their revised, authored, or reviewed attributes.
			List<GKInstance> parentPathways = (List<GKInstance>) newRLE.getReferers(ReactomeJavaConstants.hasEvent);
			for (GKInstance parentPathway : parentPathways)
			{
				// Get all non-Reactome instance edits in the revised, authored, and reviewed
				// attributes of the Pathway.
				List<GKInstance> parentPathwayIEs = getNonReactomeEventIEs(parentPathway);

				boolean hasRLEInstanceEdit = false;

				// Iterate through all new instance edits, trying to find a Pathway containing
				// them.
				for (GKInstance rleIE : newRLEInstanceEdits)
				{
					for (GKInstance pathwayIE : parentPathwayIEs)
					{
						if (rleIE.getDBID().equals(pathwayIE.getDBID()))
						{
							// The parent Pathway contained the IE.
							hasRLEInstanceEdit = true;
						}
					}
					if (hasRLEInstanceEdit)
					{
						// If it has the new IE, it might be not be the highest Pathway in the hierarchy
						// with it.
						// Continue looking up the hierarchy to try and find the HIGHEST Pathway with
						// the IE.

						List<GKInstance> grandparentPathwaysWithRLEInstanceEdit = getGrandparentPathwayWithRLEInstanceEdit(parentPathway, rleIE);
						for (GKInstance grandparentPathwayWithRLEInstanceEdit : grandparentPathwaysWithRLEInstanceEdit) {
							addPathwayToMap(pathwaysMap, newRLE, rleIE, grandparentPathwayWithRLEInstanceEdit);
						}
					}
					else
					{
						addPathwayToMap(pathwaysMap, newRLE, rleIE, parentPathway);
					}
				}
			}
		}
		return pathwaysMap;
	}

	private static void addPathwayToMap(Map<GKInstance, Set<Map<GKInstance, GKInstance>>> pathwaysMap, GKInstance newRLE, GKInstance rleIE, GKInstance grandparentPathwayWithRLEInstanceEdit)
	{
		Map<GKInstance, GKInstance> reaction2newIE = new HashMap<>();
		reaction2newIE.put(newRLE, rleIE);
		// If 'Pathway' key exists, add to it
		if (pathwaysMap.get(grandparentPathwayWithRLEInstanceEdit) != null)
		{
			pathwaysMap.get(grandparentPathwayWithRLEInstanceEdit).add(reaction2newIE);
		}
		else
		{
			// If 'Pathway' key doesn't exist, create it and then add to it.
			Set<Map<GKInstance, GKInstance>> reaction2newIESet = new HashSet<>(Arrays.asList(reaction2newIE));
			pathwaysMap.put(grandparentPathwayWithRLEInstanceEdit, reaction2newIESet);
		}
	}

	private static void printOutput(Map<GKInstance, Set<Map<GKInstance, GKInstance>>> pathway2Reactions2NewIEs) throws InvalidAttributeException, Exception
	{
		// At this point in the program you have an HashMap object that has the format
		// {Pathway => [{ReactionlikeEvent : InstanceEdit}]}
		// This will now be output in the report style that has been shared in the
		// emails.

		// At time of writing, there are 17 Pathways identified as 'needing a DOI
		// update' when using:
		// - automatedDOIs.dbName=test_slice_20210118
		// - automatedDOIs.prevDbName=slice_previous_as_74

		System.out.println(pathway2Reactions2NewIEs.keySet().size() + " DOI suggestions");
		// TODO: Replace this with proper CSV output to file.
		System.out.println("PathwayToBeUpdated\tUpdatedReactionsCount\tUpdatedReactions\tNewInstanceEdits\tMostRecentModified");
		for (Entry<GKInstance, Set<Map<GKInstance, GKInstance>>> pathwayEntry : pathway2Reactions2NewIEs.entrySet())
		{
			GKInstance pathway = pathwayEntry.getKey();
			List<String> rles = new ArrayList<>();
			Set<String> ies = new HashSet<>();

			for (Map<GKInstance, GKInstance> rle2newIE : pathwayEntry.getValue())
			{
				rles.add(rle2newIE.keySet().iterator().next().getExtendedDisplayName());
				GKInstance rle = rle2newIE.keySet().iterator().next();
				ies.add(rle2newIE.get(rle).getExtendedDisplayName());
			}

			if (pathway != null) {
				List<GKInstance> pathwayModifieds = pathway.getAttributeValuesList(ReactomeJavaConstants.modified);

				String pathwayModifications = !pathwayModifieds.isEmpty() ? (pathwayModifieds.get(pathwayModifieds.size() - 1)).toString() : "No modification instances";
				System.out.println(pathway + "\t" + pathway2Reactions2NewIEs.get(pathway).size() + "\t" + String.join("|", rles) + "\t" + String.join("|", ies) + "\t" + pathwayModifications);
			}
		}
	}

	private static boolean hasNewInstanceEdit(List<GKInstance> currentRLEInstanceEdits, List<GKInstance> previousRLEInstanceEdits)
	{
		// start by assuming false and set to true when conditions require it.
		boolean newInstanceEdit = false;
		// I guess Justin assumed that if there are more InstanceEdits of the current version of an object, the it *must* be new.
		// This is *probably* a safe assumption to make.
		if (currentRLEInstanceEdits.size() != previousRLEInstanceEdits.size())
		{
			newInstanceEdit = true;
		}
		else
		{
			int i = 0;
			while (i < currentRLEInstanceEdits.size() && !newInstanceEdit)
			{
				GKInstance currentIE = currentRLEInstanceEdits.get(i);
				GKInstance previousIE = previousRLEInstanceEdits.get(i);
				// It seems that in the case where current and previous versions have the same
				// number of InstanceEdits, comparing DBIDs indicates if an InstanceEdit is new.
				// I'm assuming Justin tested this theory more rigorously.
				if (!currentIE.getDBID().equals(previousIE.getDBID()))
				{
					newInstanceEdit = true;
				}
				i++;
			}
		}
		return newInstanceEdit;
	}

	private static List<GKInstance> getGrandparentPathwayWithRLEInstanceEdit(GKInstance parentPathway, GKInstance newIE) throws Exception
	{
		List<GKInstance> pathways = new ArrayList<>();

		List<GKInstance> grandparentPathways = (List<GKInstance>) parentPathway.getReferers(ReactomeJavaConstants.hasEvent);

		for (GKInstance grandparentPathway : grandparentPathways) {
			// This never came up in my testing, but if it does, might need to be accounted
			// for.
//			if (grandparentPathways.size() > 1)
//			{
//				System.out.println("TOO MANY ANCESTORS for " + parentPathway);
//				return null;
//				//System.exit(0);
//			}
			List<GKInstance> grandparentPathwayReviewedAuthoredRevisedInstances = grandparentPathway.getAttributeValuesList(ReactomeJavaConstants.reviewed);
			grandparentPathwayReviewedAuthoredRevisedInstances.addAll(grandparentPathway.getAttributeValuesList(ReactomeJavaConstants.authored));
			grandparentPathwayReviewedAuthoredRevisedInstances.addAll(grandparentPathway.getAttributeValuesList(ReactomeJavaConstants.revised));
			boolean hasRLEInstanceEdit = false;
			for (GKInstance grandparentPathwayIE : grandparentPathwayReviewedAuthoredRevisedInstances)
			{
				if (newIE.getDBID().equals(grandparentPathwayIE.getDBID()))
				{
					hasRLEInstanceEdit = true;
				}
			}

			if (hasRLEInstanceEdit)
			{
				// Pathway has the IE, but might not be the highest level. Recursively check
				// upwards!
				pathways.addAll(getGrandparentPathwayWithRLEInstanceEdit(grandparentPathway, newIE));
			}
			else
			{
				// Pathway does not have the IE, and according to proper rules, that should be
				// the the highest Pathway, so it is returned.
				pathways.add(parentPathway);
			}
		}
		return pathways;
	}

	private static List<GKInstance> getNonReactomeEventIEs(GKInstance event) throws Exception
	{

		List<GKInstance> instanceEdits = event.getAttributeValuesList(ReactomeJavaConstants.reviewed);
		instanceEdits.addAll(event.getAttributeValuesList(ReactomeJavaConstants.authored));
		instanceEdits.addAll(event.getAttributeValuesList(ReactomeJavaConstants.revised));

		List<GKInstance> newNonReactomeInstanceEdits = getNonReactomeIEs(instanceEdits);
		return newNonReactomeInstanceEdits;
	}

	private static List<GKInstance> getNonReactomeIEs(List<GKInstance> instanceEdits) throws InvalidAttributeException, Exception
	{
		List<GKInstance> newNonReactomeInstanceEdits = new ArrayList<>();
		for (GKInstance ie : instanceEdits)
		{
			// 'project' is single-value variable
			// TODO: Iterate through all authors
			GKInstance personInst = (GKInstance) ie.getAttributeValue(ReactomeJavaConstants.author);
			String projectText = (String) personInst.getAttributeValue("project");
			if (projectText == null || !projectText.equals("Reactome"))
			{
				newNonReactomeInstanceEdits.add(ie);
			}
		}
		return newNonReactomeInstanceEdits;
	}

	private static List<GKInstance> getNewNonReactomeInstanceEdits(GKInstance currentRLE, List<GKInstance> currentRLEReviewedInstances, List<GKInstance> currentRLEAuthoredInstances, List<GKInstance> currentRLERevisedInstances, List<GKInstance> previousRLEReviewedInstances, List<GKInstance> previousRLEAuthoredInstances, List<GKInstance> previousRLERevisedInstances) throws Exception
	{

		List<GKInstance> newInstanceEdits = new ArrayList<>();

		// Check the 'reviewed' lists.
		newInstanceEdits.addAll(findNewInstanceEdits(currentRLE, currentRLEReviewedInstances, previousRLEReviewedInstances, "Reviewed"));

		// Check the 'authored' lists.
		newInstanceEdits.addAll(findNewInstanceEdits(currentRLE, currentRLEAuthoredInstances, previousRLEAuthoredInstances, "Authored"));
		
		// Check the 'revised' lists.
		newInstanceEdits.addAll(findNewInstanceEdits(currentRLE, currentRLERevisedInstances, previousRLERevisedInstances, "Revised"));

		// Filter the lists so that only non-Reactome IEs are returned.

		List<GKInstance> newNonReactomeInstanceEdits = getNonReactomeIEs(newInstanceEdits);
		return newNonReactomeInstanceEdits;
	}

	private static List<GKInstance> findNewInstanceEdits(GKInstance currentRLE, List<GKInstance> currentRLEInstances, List<GKInstance> previousRLEInstances, String listType)
	{
		List<GKInstance> newInstanceEdits = new ArrayList<>();
		
		for (int i = 0; i < currentRLEInstances.size(); i++)
		{
			GKInstance currentIE = currentRLEInstances.get(i);
			GKInstance previousIE = null;
			if (previousRLEInstances.size() > i)
			{
				previousIE = previousRLEInstances.get(i);
				if (!currentIE.getDBID().equals(previousIE.getDBID()))
				{
					System.out.println("\'" + listType + "\' elements between current and previous versions of instance do not line up for " + currentRLE.getExtendedDisplayName());
				}
			}
			else
			{
				newInstanceEdits.add(currentIE);
			}
		}
		return newInstanceEdits;
	}

	private static boolean hasRLEInstanceEdit(GKInstance newIE, GKInstance parentPathwayIE)
	{
		return newIE.getDBID().equals(parentPathwayIE.getDBID());
	}

}
