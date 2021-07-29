@Grapes([
    @Grab(group='net.sourceforge.owlapi', module='owlapi-api', version='5.1.14'),
    @Grab(group='net.sourceforge.owlapi', module='owlapi-apibinding', version='5.1.14'),
    @Grab(group='net.sourceforge.owlapi', module='owlapi-impl', version='5.1.14'),
    @Grab(group='net.sourceforge.owlapi', module='owlapi-parsers', version='5.1.14'),
    @Grab(group='net.sourceforge.owlapi', module='owlapi-distribution', version='5.1.14'),
    @GrabConfig(systemClassLoader=true)
])

import org.semanticweb.owlapi.model.IRI
import org.semanticweb.owlapi.model.parameters.*
import org.semanticweb.elk.owlapi.*
import org.semanticweb.elk.reasoner.config.*
import org.semanticweb.owlapi.apibinding.OWLManager
import org.semanticweb.owlapi.reasoner.*
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary
import org.semanticweb.owlapi.model.*
import org.semanticweb.owlapi.io.*
import org.semanticweb.owlapi.owllink.*
import org.semanticweb.owlapi.util.*
import org.semanticweb.owlapi.search.*
import org.semanticweb.owlapi.manchestersyntax.renderer.*

def deprecationMap = [:] 
def labels = [:]

def processOntology = { id, oFile ->
	// load ontology
  def manager
  def ontology
  def config
  try {
    manager = OWLManager.createOWLOntologyManager()
    ontology = manager.loadOntologyFromOntologyDocument(new File("ontologies/", oFile))
    config = new SimpleConfiguration()
  } catch(e) {
    println "Error: failed to load $id" 
  }

  def deprecated = []

  if(!ontology) { return deprecated }

  def processEntity = { cl ->
    def iri = cl.getIRI().toString()
		EntitySearcher.getAnnotations(cl, ontology).each { anno ->
			def property = anno.getProperty()
			OWLAnnotationValue val = anno.getValue()

			if(val instanceof OWLLiteral) {
        def literal = val.getLiteral()

				if((property.isLabel() || property.toString() == "<http://www.w3.org/2004/02/skos/core#prefLabel>") && !labels.containsKey(iri)) {
					labels[iri] = literal
				}

        // Get deprecation status
        if(property.toString() == 'owl:deprecated' && literal == 'true') {
          deprecated << iri
        }
      }
		}
	}

	ontology.getClassesInSignature(false).each { cl ->
    processEntity(cl)
  }

  ontology.getObjectPropertiesInSignature(false).each { cl ->
    processEntity(cl) 
  }
		  
  return deprecated
}

def lines = []
new File('./index.tsv').splitEachLine('\t') {
	lines << it
}
lines.eachWithIndex { it, k ->
	println "Processing ${it[0]} ($k/${lines.size()})"
	deprecationMap[it[0]] = processOntology(it[0], it[1])
  if(deprecationMap.containsKey(it[0])) {
    println "  Found ${deprecationMap[it[0]].size()} deprecated terms"
  }
}

def out = []
deprecationMap.each { id, dTerms ->
  out << "$id\t${dTerms.join(';')}" 
}
new File('deprecated_classes.tsv').text = out.join('\n')
