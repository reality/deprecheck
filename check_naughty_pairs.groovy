@Grapes([
    @Grab(group='net.sourceforge.owlapi', module='owlapi-api', version='5.1.14'),
    @Grab(group='net.sourceforge.owlapi', module='owlapi-apibinding', version='5.1.14'),
    @Grab(group='net.sourceforge.owlapi', module='owlapi-impl', version='5.1.14'),
    @Grab(group='net.sourceforge.owlapi', module='owlapi-parsers', version='5.1.14'),
    @Grab(group='net.sourceforge.owlapi', module='owlapi-distribution', version='5.1.14'),

  @GrabResolver(name='sonatype-nexus-snapshots', root='https://oss.sonatype.org/service/local/repositories/snapshots/content/'),
   // @Grab('org.semanticweb.elk:elk-reasoner:0.5.0-SNAPSHOT'),
   // @Grab('org.semanticweb.elk:elk-owl-implementation:0.5.0-SNAPSHOT'),
    @Grab('au.csiro:elk-owlapi5:0.5.0'),

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
import org.semanticweb.elk.owlapi.*
import org.semanticweb.elk.reasoner.config.*

import com.clarkparsia.owlapi.explanation.BlackBoxExplanation
import com.clarkparsia.owlapi.explanation.HSTExplanationGenerator

def pairs = [:]
new File('./deprecated_uses.tsv').splitEachLine('\t') {
  def key = "${it[0]}_${it[1]}"
  def altKey = "${it[1]}_${it[0]}"

  if(!pairs[key]) {
    if(pairs[altKey]) {
      key = altKey
    } else {
      pairs[key] = [] 
    }
  }

  pairs[key] << it[2]
}

pairs.each { k, v -> v = v.unique(false) }

def superSplains = []

def reasonerFactory = new ElkReasonerFactory()
def eConf = ReasonerConfiguration.getConfiguration()
eConf.setParameter(ReasonerConfiguration.NUM_OF_WORKING_THREADS, "70")
eConf.setParameter(ReasonerConfiguration.INCREMENTAL_MODE_ALLOWED, "true")
def rConf = new ElkReasonerConfiguration(ElkReasonerConfiguration.getDefaultOwlReasonerConfiguration(new NullReasonerProgressMonitor()), eConf);
def i = 0
pairs.each { k, v ->
  println "Processing $k ($i/${pairs.size()})"
  def manager
  def ontology
  def config
  def factory 
  if(new File("mutants/${k}.owl").exists()) {
  try {
    manager = OWLManager.createOWLOntologyManager()
    factory = manager.getOWLDataFactory()
    ontology = manager.loadOntologyFromOntologyDocument(new File("mutants/${k}.owl"))
    config = new SimpleConfiguration()
  } catch(e) {
  e.printStackTrace()
  }
  }

  def oReasoner
  try {
    oReasoner = reasonerFactory.createReasoner(ontology, rConf) 
    oReasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY)
  } catch(e) {
    println '  Problem reasoning: ' + e.getMessage()
  }

  if(oReasoner && oReasoner.isConsistent()) {
    def unsats = []

    ontology.getClassesInSignature(true).collect { cl ->
      if(cl.getIRI().toString() == 'http://www.w3.org/2002/07/owl#Nothing') { return; }
      if(!oReasoner.isSatisfiable(cl)) {
        unsats << cl.getIRI().toString()
      }
    }
    
    println "  Found ${unsats.size()} unsatisfiable classes"
    def allOnts = [ontology] + ontology.getImports()

    if(unsats.size() > 0) {
			unsats = unsats.findAll { u ->
        def dC = factory.getOWLClass(IRI.create(u))

        def superClasses = allOnts.collect { it.getSubClassAxiomsForSubClass(dC) }.flatten()
				def unsatParent = superClasses.find {
          if(it.getSuperClass().isOWLClass()) {
            unsats.contains(it.getSuperClass().getIRI().toString()) 
          }
        }

        if(unsatParent){
          superSplains << "$k\t$u\t$unsatParent" 
          unsatParent = unsatParent.getSuperClass().getIRI().toString()
          return false
        } else {
          return true 
        }
			}
      new File('splainparents.tsv').text = superSplains.join('\n')

      def allExplanations = [:]
      unsats.eachWithIndex { iri, ii ->
        allExplanations[iri] = []
        def exp = new BlackBoxExplanationFix(ontology, reasonerFactory, oReasoner)
        def fexp = new HSTExplanationGenerator(exp)
        def dC = factory.getOWLClass(IRI.create(iri))

        def explanations = exp.getExplanation(dC)
        for(OWLAxiom causingAxiom : explanations) {
          allExplanations[iri] << causingAxiom.toString()
        }

      if(allExplanations[iri].any { v.any { vv -> it.indexOf(vv) != -1 } }) {
        println 'match!!!' 
      }

        println "gotexp $ii/${unsats.size()}"
        new File("exps/${k}_${iri.tokenize('/').last()}.txt").text = allExplanations[iri].join('\n')
      } 
    }
  }
}

