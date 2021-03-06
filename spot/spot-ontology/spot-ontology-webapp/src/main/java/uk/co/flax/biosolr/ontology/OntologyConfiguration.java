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
package uk.co.flax.biosolr.ontology;

import uk.co.flax.biosolr.ontology.config.JenaConfiguration;
import uk.co.flax.biosolr.ontology.config.SolrConfiguration;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.dropwizard.Configuration;

/**
 * @author Matt Pearce
 *
 */
public class OntologyConfiguration extends Configuration {
	
	@JsonProperty
	private String urlPattern;
	
	@JsonProperty("solr")
	private SolrConfiguration solr;
	
	@JsonProperty("jena")
	private JenaConfiguration jena;
	
	public String getUrlPattern() {
		return urlPattern;
	}
	
	public SolrConfiguration getSolr() {
		return solr;
	}
	
	public JenaConfiguration getJena() {
		return jena;
	}
	
}
