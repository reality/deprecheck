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

def index = [:]
new File('./index.tsv').splitEachLine('\t') {
	index[it[0]] = it[1]
}

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

def COMBO_PREFIX = 'http://reality.rehab/ontology/'
def COMBO_FOLDER = 'combos/'
def PURL = "http://purl.obolibrary.org/obo/"

def manager = OWLManager.createOWLOntologyManager()
def config = new OWLOntologyLoaderConfiguration()
config.setFollowRedirects(true)

pairs.each { k, v ->
try {
  def key = k.tokenize('_')
  def oFile = index[key[0]]
  def tFile = index[key[1]]
  
  println "${key[0]}_${key[1]}"
  def fileFormatted = new File("mutants/${key[0]}_${key[1]}.owl")
  if(fileFormatted.exists()) { return;}

  def importDeclaration = manager.getOWLDataFactory().getOWLImportsDeclaration(IRI.create("file:///home/luke/deprecheck/ontologies/${tFile}"))
  def childOntology = manager.loadOntologyFromOntologyDocument(new File("ontologies", oFile))
  def newOntologyID = IRI.create("http://reality.rehab/ontologies/${key[0]}_${key[1]}")

  def iDecs = childOntology.getImportsDeclarations()
  iDecs.each { imp ->
    def it = manager.getImportedOntology(imp)
    manager.applyChange(new RemoveImport(childOntology, imp)) 
  }

  def newOntology = new OWLOntologyMerger(manager).createMergedOntology(manager, newOntologyID)

  manager.applyChange(new AddImport(newOntology, importDeclaration))

  manager.saveOntology(newOntology, IRI.create(fileFormatted.toURI()))

  manager.clearOntologies()

  println "Saved ${fileFormatted.getName()}"
  } catch(e){println "failed to make combo $key"}
}
