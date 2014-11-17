/**
 * Copyright (C) 2014 The SciGraph authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.sdsc.scigraph.owlapi.loader;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.validator.routines.UrlValidator;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.AddAxiom;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLAxiomChange;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.RemoveAxiom;
import org.semanticweb.owlapi.reasoner.Node;
import org.semanticweb.owlapi.reasoner.NodeSet;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.util.OWLOntologyWalker;

import com.google.inject.Inject;

import edu.sdsc.scigraph.owlapi.FileCachingIRIMapper;

public class OwlOntologyWalkerProducer implements Callable<Void>{

  private static final Logger logger = Logger.getLogger(OwlOntologyWalkerProducer.class.getName());

  private final BlockingQueue<OWLOntologyWalker> queue;
  private final BlockingQueue<String> urlQueue;
  private final int numConsumers;
  private final UrlValidator validator = UrlValidator.getInstance();
  private final static OWLReasonerFactory reasonerFactory = new ElkReasonerFactory();
  private final static OWLDataFactory factory = OWLManager.getOWLDataFactory();

  @Inject
  OwlOntologyWalkerProducer(BlockingQueue<OWLOntologyWalker> queue, BlockingQueue<String> urlQueue, int numConsumers) {
    this.queue = queue;
    this.urlQueue = urlQueue;
    this.numConsumers = numConsumers;
  }

  public static void addDirectInferredEdges(OWLOntologyManager manager, OWLOntology ont) {
    org.apache.log4j.Logger.getLogger("org.semanticweb.elk").setLevel(org.apache.log4j.Level.FATAL);

    logger.info("Creating reasoner for " + ont);
    OWLReasoner reasoner = reasonerFactory.createReasoner(ont);
    logger.info("Completed creating reasoner for " + ont);

    List<OWLAxiomChange> batchedAdds = new LinkedList<>();
    List<OWLAxiomChange> batchedRemoves = new LinkedList<>();
    //For each class x
    for (OWLClass ce: ont.getClassesInSignature(true)) {
      //find direct inferred superclasses, D
      NodeSet<OWLClass> directSuperclasses = reasoner.getSuperClasses(ce, true);
      //find indirect superclasses, I
      NodeSet<OWLClass> indirectSuperclasses = reasoner.getSuperClasses(ce, false);
      //find asserted superclasses, A
      Set<OWLClassExpression> assertedSuperclasses = ce.asOWLClass().getSuperClasses(ont);
      //for each d in D, add an edge SubClassOf(x d)
      for (Node<OWLClass> directSuperclass: directSuperclasses) {
        OWLAxiom axiom = factory.getOWLSubClassOfAxiom(ce, directSuperclass.getRepresentativeElement());
        AddAxiom addAxiom = new AddAxiom(ont, axiom);
        //manager.applyChange(addAxiom);
        batchedAdds.add(addAxiom);
      }
      //for each a in A
      for (OWLClassExpression assertedSuperclass: assertedSuperclasses) {
        if (assertedSuperclass.isAnonymous()) {
          continue;
        }
        //if a is in I and not in D, then remove edge SubClassOf(x a)
        if (indirectSuperclasses.containsEntity(assertedSuperclass.asOWLClass()) &&
            !directSuperclasses.containsEntity(assertedSuperclass.asOWLClass())) {
          OWLAxiom axiom = factory.getOWLSubClassOfAxiom(ce, assertedSuperclass);
          RemoveAxiom removeAxiom = new RemoveAxiom(ont, axiom);
          //manager.applyChange(removeAxiom);
          batchedRemoves.add(removeAxiom);
        }
      }
    }
    logger.info("Applying superclass axioms: adds " + batchedAdds.size() + " removes " + batchedRemoves.size());
    //manager.applyChanges(batchedChanges);
    logger.info("Completed applying superclass axioms");
    reasoner.dispose();
  }

  @Override
  public Void call() throws Exception {
    try {
      while (true) {
        String url = urlQueue.take();
        if (BatchOwlLoader.POISON_STR == url) {
          break;
        } else {
          OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
          //manager.addIRIMapper(new FileCachingIRIMapper());
          logger.info("Loading ontology: " + url);
          try {
            OWLOntology ont = null;
            if (validator.isValid(url)) {
              ont = manager.loadOntology(IRI.create(url));
            } else {
              ont = manager.loadOntologyFromOntologyDocument(new File(url));
            }
            // TODO: fix this
            // addDirectInferredEdges(manager, ont);
            queue.put(new OWLOntologyWalker(manager.getOntologies()));
          } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to load ontology: " + url, e);
          }
        }
      }
    } catch (Exception e) { 
      logger.log(Level.WARNING, "Failed to load ontology", e);
    }
    finally {
      int poisonCount = numConsumers;
      while (true) {
        try {
          while (poisonCount-- > 0) {
            queue.put(BatchOwlLoader.POISON);
          }
          break;
        } catch (InterruptedException e1) { /* Retry */}
      }
    }
    return null;
  }

}