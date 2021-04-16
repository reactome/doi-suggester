package org.reactome.qa.doisuggester;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.QuoteMode;
import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.InvalidAttributeException;
import org.reactome.qa.doisuggester.Suggester.Suggestion;
import org.reactome.util.general.MandatoryProperties;
import org.reactome.util.general.MandatoryProperties.PropertyHasNoValueException;
import org.reactome.util.general.MandatoryProperties.PropertyNotPresentException;

public class DOISuggesterMain
{

	public static void main(String[] args) throws IOException
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
			Collection<GKInstance> reactionLikeEvents = (Collection<GKInstance>) dbAdaptor.fetchInstancesByClass(ReactomeJavaConstants.ReactionlikeEvent);
			// Sort them so that you'll get consistent results when testing.
			List<GKInstance> rleList = new ArrayList<>(reactionLikeEvents);
			InstanceUtilities.sortInstances(rleList);

			// Structure for output
			System.out.println(rleList.size() + " Reaction-like events will be checked.");
			Map<String, Integer> rleCountsForPathways = new HashMap<>();
			List<List<String>> records = new ArrayList<>();
				
			for (GKInstance currentRLE : rleList)
			{
				// Ignore inferred RLEs
				if (currentRLE.getReferers(ReactomeJavaConstants.inferredFrom) == null)
				{
					ReactionlikeEvent rle = new ReactionlikeEvent(currentRLE, dbAdaptorPrev);
					Suggester suggester = new Suggester(rle);
					Set<Suggestion> rleSuggestions = suggester.getSuggestion();
					for (Suggestion s : rleSuggestions)
					{
						String pathway = s.getPathway();
						rleCountsForPathways.computeIfAbsent(pathway, x -> Integer.valueOf(0));
						for (String rleKey : s.getRlesToInstanceEdits().keySet())
						{
							String instanceEdits = s.getRlesToInstanceEdits().get(rleKey).stream().reduce("", (prev, instEd) -> prev + " | " + instEd );
							List<String> record = new ArrayList<>();
							record.add(pathway);
							record.add(rleKey);
							record.add(instanceEdits);
							records.add(record);
							rleCountsForPathways.put(pathway, rleCountsForPathways.get(pathway) + 1);
						}
					}
				}
			}
			
			int total = 0;
			for (Entry<String, Integer> entry : rleCountsForPathways.entrySet() )
			{
				System.out.println(entry.getValue() + " RLEs for " + entry.getKey());
				total += entry.getValue();
			}
			System.out.println( total + " total RLEs have DOI suggestion.");
			printOutput(records);
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
		System.out.println("All done.");
	}

	/**
	 * Print the suggestions to a file.
	 * @param suggestions
	 * @throws IOException
	 */
	private static void printOutput(List<List<String>> records) throws IOException
	{
		final int pathwayIndex = 0;
		final int rleIndex = 1;
		final int instanceEditIndex = 2;
		try (CSVPrinter printer = new CSVPrinter(new FileWriter(new File("doi-suggestions.csv")), CSVFormat.DEFAULT.withQuoteMode(QuoteMode.ALL).withHeader("Pathway","RLE", "InstanceEdits")))
		{
			Comparator<List<String>> recordComparator = (arg0, arg1) -> {
					// first compare the pathway field
					int compareResult = arg0.get(pathwayIndex).compareTo(arg1.get(pathwayIndex));
					if (compareResult == 0)
					{
						// if they match, compare the ReactionlikeEvent.
						compareResult = arg0.get(rleIndex).compareTo(arg1.get(rleIndex));
						//...no need to go further and compare InstanceEdits because all of the instanceEdits for an RLE are already concatenated into a single string.
					}
					return compareResult;
				};
			
			for (List<String> record : records.stream().sorted(recordComparator).collect(Collectors.toList()))
			{
				printer.printRecord(record.get(pathwayIndex), record.get(rleIndex), record.get(instanceEditIndex));
			}
		}
	}
}
