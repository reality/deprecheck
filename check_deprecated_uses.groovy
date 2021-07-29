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

def lines = []
new File('./index.tsv').splitEachLine('\t') {
	lines << it
}
def deprecationMap = [:]
new File('deprecated_classes.tsv').splitEachLine('\t') {
  if(it[1]) {
    deprecationMap[it[0]] = it[1].tokenize(';')
  }
}

def processOntology = { id, oFile ->
	// load ontology
  def manager
  def ontology
  def config
  def factory 
  try {
    manager = OWLManager.createOWLOntologyManager()
    factory = manager.getOWLDataFactory()
    ontology = manager.loadOntologyFromOntologyDocument(new File("ontologies/", oFile))
    config = new SimpleConfiguration()
  } catch(e) {
    println "Error: failed to load $id" 
  }

  def deprecatedUses = []

  if(!ontology) { return deprecatedUses }

  deprecationMap.each { oId, dCls ->
    if(oId == id) { return; } 

def total = 0
def anonpas = 0
    dCls.each { iri ->
    total++
      def iIri = IRI.create(iri)
      def cl
      if(ontology.containsClassInSignature(iIri, false)) {
        cl = factory.getOWLClass(iIri)
      } else if(ontology.containsObjectPropertyInSignature(iIri, false)) {
        cl = factory.getOWLObjectProperty(iIri)
      }

      if(!cl) { return; }

  anonpas++

      def deprecated = false

      EntitySearcher.getAnnotations(cl, ontology).each { anno ->
        def property = anno.getProperty()
        OWLAnnotationValue val = anno.getValue()

        if(val instanceof OWLLiteral) {
          def literal = val.getLiteral()

          // Get deprecation status
          if(property.toString() == 'owl:deprecated' && literal == 'true') {
            deprecated = true
          }
        }
      }

      if(!deprecated) {
        deprecatedUses << "$oId\t$iri" 
      }
    } 

   // println "$anonpas/$total"
  }
  return deprecatedUses
}


def deprecatedEntityUses = [:]
lines.eachWithIndex { it, k ->
	println "Processing ${it[0]} ($k/${lines.size()})"
	deprecatedEntityUses[it[0]] = processOntology(it[0], it[1])
  if(deprecatedEntityUses.containsKey(it[0])) {
    println "  Found ${deprecatedEntityUses[it[0]].size()} deprecated entity uses"
    deprecatedEntityUses[it[0]].each {
      println it 
    }
  }
}

def out = []
deprecatedEntityUses.each { id, uses ->
  uses.each {
    out << "$id\t" + it
  }
}
new File('deprecated_uses.tsv').text = out.join('\n')

