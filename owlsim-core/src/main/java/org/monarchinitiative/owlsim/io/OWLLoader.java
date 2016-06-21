package org.monarchinitiative.owlsim.io;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.monarchinitiative.owlsim.kb.BMKnowledgeBase;
import org.monarchinitiative.owlsim.kb.impl.BMKnowledgeBaseOWLAPIImpl;
import org.phenopackets.api.PhenoPacket;
import org.phenopackets.api.io.JsonReader;
import org.phenopackets.api.io.YamlReader;
import org.phenopackets.api.model.association.PhenotypeAssociation;
import org.phenopackets.api.model.ontology.OntologyClass;
import org.phenopackets.api.util.ContextUtil;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClassAssertionAxiom;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;

import com.github.jsonldjava.core.Context;
import com.github.jsonldjava.core.JsonLdError;
import com.google.common.base.Preconditions;

/**
 * Object for loading OWL ontologies into a {@link BMKnowledgeBase}
 * 
 * Note that a KB consists of classes and individuals, both of which can be loaded
 * from an ontology
 * 
 * @author cjm
 *
 */
public class OWLLoader {
	private Logger LOG = Logger.getLogger(OWLLoader.class);

	OWLOntologyManager manager;
	OWLOntology owlOntology;
	OWLOntology owlDataOntology;
	OWLReasoner owlReasoner;
	OWLReasonerFactory owlReasonerFactory = new ElkReasonerFactory();

	/**
	 * @param iri
	 * @return OWL Ontology 
	 * @throws OWLOntologyCreationException
	 */
	public OWLOntology loadOWL(IRI iri) throws OWLOntologyCreationException {
		return getOWLOntologyManager().loadOntology(iri);
	}

	/**
	 * @param file
	 * @return OWL Ontology
	 * @throws OWLOntologyCreationException
	 */
	public OWLOntology loadOWL(File file) throws OWLOntologyCreationException {
		IRI iri = IRI.create(file);
		return getOWLOntologyManager().loadOntologyFromOntologyDocument(iri);	    
	}

	/**
	 * Loads an OWL ontology from a URI or file
	 * 
	 * @param path
	 * @return OWL Ontology
	 * @throws OWLOntologyCreationException
	 */
	public OWLOntology loadOWL(String path) throws OWLOntologyCreationException {
		if (path.startsWith("http")) {
			return loadOWL(IRI.create(path));
		}
		else {
			File file = new File(path);
			return loadOWL(file);
		}
	}

	/**
	 * @param iri
	 * @throws OWLOntologyCreationException
	 */
	public void load(IRI iri) throws OWLOntologyCreationException {
		owlOntology = getOWLOntologyManager().loadOntology(iri);
		Preconditions.checkNotNull(owlOntology);	    
	}

	/**
	 * @param file
	 * @throws OWLOntologyCreationException
	 */
	public void load(File file) throws OWLOntologyCreationException {
		owlOntology = loadOWL(file);
		Preconditions.checkNotNull(owlOntology);	    
	}



	/**
	 * Loads an OWL ontology from a URI or file
	 * 
	 * @param path
	 * @throws OWLOntologyCreationException
	 */
	public void load(String path) throws OWLOntologyCreationException {
		owlOntology = loadOWL(path);
		Preconditions.checkNotNull(owlOntology);	    
	}
	
	/**
	 * Loads OWL ontologies from a URI or file
	 * 
	 * @param path
	 * @throws OWLOntologyCreationException
	 */
	public void loadOntologies(String... paths) throws OWLOntologyCreationException {
		for (String path : paths)
			mergeOntology( loadOWL(path) );
		Preconditions.checkNotNull(owlOntology);	    
	}


	/**
	 * Loads an OWL ontology from a URI or file
	 * 
	 * @param path
	 * @throws OWLOntologyCreationException
	 */
	public void loadData(String... paths) throws OWLOntologyCreationException {
		for (String path : paths)
			mergeData( loadOWL(path) );
		Preconditions.checkNotNull(owlDataOntology);	    
	}
	
	public void loadDataFromTsv(String path) throws OWLOntologyCreationException, IOException {
		File f = new File(path);
		List<String> lines = FileUtils.readLines(f);
		for (String line : lines) {
			String[] vals = line.split("\t", 2);
			String[] terms = vals[1].split(";");
			for (String t : terms) {
				addInstanceOf(vals[0], t);
			}
		}
		Preconditions.checkNotNull(owlDataOntology);	    
	}

	public void loadDataFromPhenoPacket(String path) throws IOException, JsonLdError {
		File f = new File(path);
		PhenoPacket packet;
		try {
			packet = JsonReader.readFile(f);
		} catch (IOException e) {
			packet = YamlReader.readFile(f);
		}
		Context context = ContextUtil.getJSONLDContext(packet);
		for (PhenotypeAssociation association : packet.getPhenotypeAssociations()) {
			String instance = ContextUtil.expandIdentifierAsValue(association.getEntityId(), context);
			for (OntologyClass type : association.getPhenotype().getTypes()) {
				// PhenoPacket types are not expanded as JSON-LD @type fields
				String term = ContextUtil.expandIdentifierAsValue(type.getId(), context);
				addInstanceOf(instance, term);
			}
		}
	}
	
	private IRI getIRI(String id) {
		// TODO - use json-ld
		if (id.contains(":")) {
			return IRI.create("http://purl.obolibrary.org/obo/"+id.replace(":", "_"));
		}
		else {
			return IRI.create(id);
		}
	}

	private void mergeOntology(OWLOntology o) {
		if (owlOntology == null) {
			LOG.info("Ont ontology="+o);
			owlOntology = o;
		}
		else {
			LOG.info("Merging ont axioms from="+o);
			owlOntology.getOWLOntologyManager().addAxioms(owlOntology, o.getAxioms());
		}
	}
	
	private void addInstanceOf(String i, String c) {
		if (owlDataOntology == null) {
			owlDataOntology = owlOntology;
		}
		OWLDataFactory f = manager.getOWLDataFactory();
		OWLClassAssertionAxiom ax = f.getOWLClassAssertionAxiom(f.getOWLClass(getIRI(c)), f.getOWLNamedIndividual(getIRI(i)));
		manager.addAxiom(owlOntology, ax);
	}


	private void mergeData(OWLOntology o) {
		if (owlDataOntology == null) {
			LOG.info("Data ontology="+o);
			owlDataOntology = o;
		}
		else {
			LOG.info("Merging data axioms from="+o);
			owlDataOntology.getOWLOntologyManager().addAxioms(owlDataOntology, o.getAxioms());
		}
	}

	private OWLOntologyManager getOWLOntologyManager() {
		if (manager == null)
			manager = OWLManager.createOWLOntologyManager();
		return manager;
	}

	/**
	 * @return handle for a Bitmap-based Knowledge Base
	 */
	public BMKnowledgeBase createKnowledgeBaseInterface() {
		// TODO: use factories, or injection

		return BMKnowledgeBaseOWLAPIImpl.create(owlOntology, owlDataOntology, owlReasonerFactory);
	}
}
