package org.reactome.qa.doisuggester;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

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
			Map<String, String> suggestions = new HashMap<>();
			System.out.println(rleList.size() + " Reaction-like events will be checked.");
			
			for (GKInstance currentRLE : rleList)
			{
				// Ignore inferred RLEs
				if (currentRLE.getReferers(ReactomeJavaConstants.inferredFrom) == null)
				{
					// Just a quick check: how many RLEs have > 1 parent via hasEvent?
					List<GKInstance> parents = (List<GKInstance>) currentRLE.getReferers(ReactomeJavaConstants.hasEvent);
//					if (parents != null && parents.size() > 1)
//					{
//						System.out.println("More than 1 parent: " + currentRLE.toString() + " has " + parents.size());
//					}
					
					
					ReactionlikeEvent rle = new ReactionlikeEvent(currentRLE, dbAdaptorPrev);
					Suggester suggester = new Suggester(rle);
					Set<Suggestion> rleSuggestions = suggester.getSuggestion();
					for (Suggestion s : rleSuggestions)
					{
						String pathway = s.getPathway();
						 
						for (String rleKey : s.getRlesToInstanceEdits().keySet())
						{
							String instanceEdits = s.getRlesToInstanceEdits().get(rleKey).stream().reduce("", String::join) + "|";
						
							suggestions.computeIfAbsent(pathway, x -> rleKey + " " + instanceEdits + "\n");
							
							if (suggestions.containsKey(pathway))
							{
								
								suggestions.put(pathway, suggestions.get(pathway) + " " + rleKey + " " + instanceEdits + ")\n");
							
							}
							else
							{
								suggestions.put(pathway, rleKey + " " + instanceEdits + "\n");
							}
						}
					}
					
//					if (suggestion != null && !suggestion.trim().equals(""))
//					{
//						System.out.println(suggestion);
//					}
				}
			}
			System.out.println(suggestions.size() + " top-level pathways have suggestions under them.");
//			for (Entry<String, String> entry : suggestions.entrySet())
//			{
//				System.out.println(entry.getKey() + "\t" + entry.getValue());
//			}
			try (CSVPrinter printer = new CSVPrinter(new FileWriter(new File("doi-suggestions.csv")), CSVFormat.DEFAULT.withQuoteMode(QuoteMode.ALL)))
			{
				for (Entry<String, String> entry : suggestions.entrySet())
				{
					printer.printRecord(entry.getKey(), entry.getValue());
				}
			}
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
}
