package edu.stanford.bmir.protege.web.server.owlapi;

import edu.stanford.bmir.protege.web.client.rpc.data.EntityData;
import edu.stanford.bmir.protege.web.client.rpc.data.UserId;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.Node;
import org.semanticweb.owlapi.reasoner.NodeSet;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

import java.util.ArrayList;
import java.util.List;

/**
 * Author: Matthew Horridge<br>
 * Stanford University<br>
 * Bio-Medical Informatics Research Group<br>
 * Date: 11/04/2012
 */
public class GetIndividualsStrategy extends OntologyServiceStrategy<List<EntityData>> {

    private String className;
    
    public GetIndividualsStrategy(OWLAPIProject project, UserId userId, String className) {
        super(project, userId);
        this.className = className;
    }

    @Override
    public List<EntityData> execute() {
        List<EntityData> result = new ArrayList<EntityData>();

        OWLAPIProject project = getProject();
        RenderingManager rm = project.getRenderingManager();
        OWLClass cls = rm.getEntity(className, EntityType.CLASS);
        OWLOntology rootOntology = getProject().getRootOntology();
        for(OWLIndividual individual : cls.getIndividuals(rootOntology.getImportsClosure())) {
            EntityData entityData = null;
            if(individual.isAnonymous()) {
                entityData = rm.getEntityData(individual.asOWLAnonymousIndividual());
            }
            else {
                entityData = rm.getEntityData(individual.asOWLNamedIndividual());
            }
            result.add(entityData);
        }
        return result;
    }
}
