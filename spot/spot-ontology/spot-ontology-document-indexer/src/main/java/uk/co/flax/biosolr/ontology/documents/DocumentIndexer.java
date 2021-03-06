/**
 * Copyright (c) 2014 Lemur Consulting Ltd.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.co.flax.biosolr.ontology.documents;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.response.UpdateResponse;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.ebi.fgpt.owl2json.OntologyHierarchyNode;
import uk.co.flax.biosolr.ontology.api.Document;
import uk.co.flax.biosolr.ontology.config.DatabaseConfiguration;
import uk.co.flax.biosolr.ontology.config.IndexerConfiguration;
import uk.co.flax.biosolr.ontology.config.loaders.ConfigurationLoader;
import uk.co.flax.biosolr.ontology.config.loaders.YamlConfigurationLoader;
import uk.co.flax.biosolr.ontology.indexer.OntologyIndexingException;
import uk.co.flax.biosolr.ontology.indexer.RelatedItem;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Matt Pearce
 *
 */
public class DocumentIndexer {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(DocumentIndexer.class);
	
	private static final int BATCH_SIZE = 1000;
	private static final int COMMIT_WITHIN = 60000;
	
	private final IndexerConfiguration config;
	
	private final SolrServer solrServer;
	private final OntologyHandler ontologyHandler;
	
	public DocumentIndexer(String configFilepath) throws IOException, OWLOntologyCreationException {
		this.config = readConfig(configFilepath);
		solrServer = new HttpSolrServer(config.getDocumentsSolrUrl());
		ontologyHandler = new OntologyHandler(config.getOntologyUri());
	}

	private IndexerConfiguration readConfig(String yamlFile) throws IOException {
		ConfigurationLoader configLoader = new YamlConfigurationLoader(yamlFile);
		return configLoader.loadConfiguration();
	}
	
	public void run() throws SQLException, IOException, SolrServerException {
		Connection dbConnection = createConnection(config.getDatabase());
		if (dbConnection == null) {
			throw new OntologyIndexingException("Connection could not be instantiated.");
		}
		
		ResultSet rs = getDocuments(dbConnection);
		int count = 0;
		List<Document> documents = extractDocuments(rs);
		while (documents.size() > 0) {
			indexDocuments(documents);
			count += documents.size();
			LOGGER.debug("Indexed {} documents", count);
			
			// Get the next batch
			documents = extractDocuments(rs);
		}
		
		// Send a final commit() to Solr
		solrServer.commit();
	}
	
	private Connection createConnection(DatabaseConfiguration dbConfig) {
		Connection conn = null;
		
		try {
			Class.forName(dbConfig.getDriver());
			conn = DriverManager.getConnection(dbConfig.getUrl(), dbConfig.getUsername(), dbConfig.getPassword());
		} catch (ClassNotFoundException e) {
			LOGGER.error("JDBC Driver class not found: {}", e.getMessage());
		} catch (SQLException e) {
			LOGGER.error("SQL exception getting connection: {}", e.getMessage());
		}
		
		return conn;
	}
	
	private ResultSet getDocuments(Connection conn) throws SQLException {
		PreparedStatement pStmt = conn.prepareStatement("select ROWNUM, ID, STUDYID, STUDY, FIRST_AUTHOR, PUBLICATION, TITLE, SNP, DISEASETRAIT, PVALUEFLOAT, EFOURI from "
						+ " (select distinct g.ID, st.ID as STUDYID, st.PMID as STUDY, st.AUTHOR as FIRST_AUTHOR, st.PUBLICATION, st.LINKTITLE as TITLE, s.SNP, t.DISEASETRAIT, g.PVALUEFLOAT, e.EFOURI from GWASSNP s"
						+ " join GWASSNPXREF sx on s.ID=sx.SNPID"
						+ " join GWASSTUDIESSNP g on sx.GWASSTUDIESSNPID=g.ID"
						+ " join GWASSTUDIES st on g.GWASID=st.ID"
						+ " join GWASDISEASETRAITS t on st.DISEASEID=t.ID"
						+ " join GWASEFOSNPXREF ex on ex.GWASSTUDIESSNPID = g.ID"
						+ " join GWASEFOTRAITS e on e.ID = ex.TRAITID"
						+ " where g.ID is not null and s.SNP is not null"
						+ " and t.DISEASETRAIT is not null and g.PVALUEFLOAT is not null and st.publish = 1)");
		ResultSet rs = pStmt.executeQuery();
		return rs;
	}
	
	private List<Document> extractDocuments(ResultSet rs) throws SQLException {
		List<Document> documents = new ArrayList<>(BATCH_SIZE);
		
		for (int i = 0; i < BATCH_SIZE; i ++) {
			if (rs.next()) {
				Document doc = new Document();
				doc.setId("" + rs.getInt("rownum"));
				doc.setGid(rs.getInt("id"));
				doc.setStudyId(rs.getInt("studyid"));
				doc.setStudy(rs.getInt("study"));
				doc.setFirstAuthor(rs.getString("first_author"));
				doc.setPublication(rs.getString("publication"));
				doc.setTitle(rs.getString("title"));
				doc.setSnp(rs.getString("snp"));
				doc.setDiseaseTrait(rs.getString("diseasetrait"));
				doc.setpValue(rs.getDouble("pvaluefloat"));
				String efoUri = rs.getString("efouri");
				doc.setEfoUri(efoUri);
				String uriHash = DigestUtils.md5Hex(efoUri);
				doc.setEfoUriHash(uriHash);
				doc.setUriKey(uriHash.hashCode());
				
				OWLClass efoClass = findEfoClass(efoUri);
				if (efoClass != null) {
					doc.setEfoLabels(lookupEfoLabels(efoClass));
					
					List<OntologyHierarchyNode> childHierarchy = ontologyHandler.getChildHierarchy(efoClass);
					doc.setChildHierarchy(convertHierarchyToJson(childHierarchy));
					doc.setChildLabels(extractLabels(childHierarchy));
					doc.setParentLabels(lookupParentLabels(efoClass));
					List<String> facetLabels = new ArrayList<String>();
					facetLabels.addAll(doc.getEfoLabels());
					facetLabels.addAll(doc.getParentLabels());
					doc.setFacetLabels(facetLabels);
					addRelatedItemsToDocument(lookupRelatedItems(efoClass), doc);
				}
				
				documents.add(doc);
			}
		}
		
		return documents;
	}
	
	private OWLClass findEfoClass(String uri) {
		return ontologyHandler.findOWLClass(uri);
	}
	
	private List<String> lookupEfoLabels(OWLClass efoClass) {
		return new ArrayList<String>(ontologyHandler.findLabels(efoClass));
	}
	
	private List<String> lookupParentLabels(OWLClass efoClass) {
		return new ArrayList<String>(ontologyHandler.findParentLabels(efoClass, false));
	}
	
	private Map<String, List<RelatedItem>> lookupRelatedItems(OWLClass efoClass) {
		return ontologyHandler.getRestrictions(efoClass);
	}
	
	private void addRelatedItemsToDocument(Map<String, List<RelatedItem>> relatedItems, Document doc) {
		Map<String, List<String>> iriMap = new HashMap<>();
		Map<String, List<String>> labelMap = new HashMap<>();
		
		for (String relation : relatedItems.keySet()) {
			List<RelatedItem> items = relatedItems.get(relation);
			List<String> iris = new ArrayList<>(items.size());
			List<String> labels = new ArrayList<>();
			
			for (RelatedItem item : relatedItems.get(relation)) {
				iris.add(item.getIri().toString());
				labels.addAll(item.getLabels());
			}
			
			iriMap.put(relation + Document.RELATED_IRI_SUFFIX, iris);
			labelMap.put(relation + Document.RELATED_LABEL_SUFFIX, labels);
		}
		
		doc.setRelatedIris(iriMap);
		doc.setRelatedLabels(labelMap);
	}
	
    private void indexDocuments(List<Document> documents) throws IOException, SolrServerException {
    	UpdateResponse response = solrServer.addBeans(documents, COMMIT_WITHIN);
		if (response.getStatus() != 0) {
			throw new OntologyIndexingException("Solr error adding records: " + response);
		}
    }
    
    private String convertHierarchyToJson(List<OntologyHierarchyNode> hierarchy) {
    	String json = null;
    	
    	if (hierarchy.size() > 0) {
    		try {
    			ObjectMapper mapper = new ObjectMapper();
    			json = mapper.writeValueAsString(hierarchy);
    		} catch (JsonProcessingException e) {
    			LOGGER.error("Caught JSON processing exception converting hierarchy: {}", e.getMessage());
    		}
    	}
    	
    	return json;
    }
    
    private List<String> extractLabels(Collection<OntologyHierarchyNode> hierarchy) {
    	List<String> labels = new ArrayList<>();
    	
    	for (OntologyHierarchyNode node : hierarchy) {
    		if (node.getSize() > 0) {
    			labels.add(node.getName());
        		labels.addAll(extractLabels(node.getChildren()));
    		}
    	}
    	
    	return labels;
    }
    
	public static void main(String[] args) {
		if (args.length != 1) {
			System.out.println("Usage:");
			System.out.println("  java DocumentIndexer config.properties");
			System.exit(1);
		}

		try {
			DocumentIndexer indexer = new DocumentIndexer(args[0]);
			indexer.run();
		} catch (IOException e) {
			LOGGER.error("IO Exception indexing documents: " + e.getMessage());
		} catch (SolrServerException e) {
			LOGGER.error("Solr exception indexing documents: " + e.getMessage());
		} catch (SQLException e) {
			LOGGER.error("SQL exception getting documents: {}", e.getMessage());
		} catch (OWLOntologyCreationException e) {
			LOGGER.error("Ontology creation exception: {}", e.getMessage());
		}
	}

}
