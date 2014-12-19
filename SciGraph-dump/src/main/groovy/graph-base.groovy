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
@Grab('edu.sdsc:scigraph-core:1.1-SNAPSHOT')
import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.traversal.Uniqueness;
import groovy.util.logging.Slf4j;
import edu.sdsc.scigraph.owlapi.OwlRelationships;

@Slf4j
class Graph {

    def graphDb
    def engine

    def init(graphLocation) {
        graphDb = new GraphDatabaseFactory().newEmbeddedDatabase(graphLocation)
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() { graphDb.shutdown() }
        })
        engine = new ExecutionEngine(graphDb)
    }

    def executeQuery(query) {
        engine.execute(query).iterator()
    }

    def shutdown() {
        graphDb.shutdown()
    }

    def executeQuery(query, Closure func) {
        def result = executeQuery query
        result.each { func(it) }
    }

    def getSuperclasses(nodeId) {
        def superClasses = [] as Set
        def superClassLabels = [] as Set
        Transaction tx = graphDb.beginTx()
        try {
            for (Path path: graphDb.traversalDescription()
                    .depthFirst()
                    .relationships(OwlRelationships.RDF_SUBCLASS_OF, Direction.OUTGOING)
                    .uniqueness(Uniqueness.NODE_GLOBAL)
                    .traverse(graphDb.getNodeById(nodeId))) {
                superClasses.add((String)path.endNode().getProperty("fragment"))
                if (path.endNode().hasProperty('label'))
                    superClassLabels.add((String)path.endNode().getProperty("label"))
            }
            tx.success()
        } finally {
            tx.close()
        }
        return [superClasses, superClassLabels]
    }

}
