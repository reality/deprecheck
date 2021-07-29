import groovy.json.*

def i = new JsonSlurper().parse(new File('./ontologies.jsonld'))
i.ontologies.each {
  if(it.activity_status != "active") { return; }

  pMatch = it.products.find { p -> p.ontology_purl =~ 'owl' }
  if(!pMatch) {
    pMatch = it.products.find { p -> p.ontology_purl =~ 'obo' }
  }

  println "$it.id\t${pMatch.id}\t${pMatch.ontology_purl}" 
}
