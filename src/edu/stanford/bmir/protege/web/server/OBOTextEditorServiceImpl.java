package edu.stanford.bmir.protege.web.server;

import edu.stanford.bmir.protege.web.client.rpc.OBOTextEditorService;
import edu.stanford.bmir.protege.web.client.rpc.data.NotSignedInException;
import edu.stanford.bmir.protege.web.client.rpc.data.ProjectId;
import edu.stanford.bmir.protege.web.client.rpc.data.UserId;
import edu.stanford.bmir.protege.web.client.rpc.data.obo.*;
import edu.stanford.bmir.protege.web.client.rpc.data.primitive.*;
import edu.stanford.bmir.protege.web.server.obo.OBONamespaceCache;
import edu.stanford.bmir.protege.web.server.owlapi.OWLAPIProject;
import edu.stanford.bmir.protege.web.server.owlapi.OWLAPIProjectManager;
import org.coode.owlapi.obo.parser.IDSpaceManager;
import org.coode.owlapi.obo.parser.OBOIdType;
import org.coode.owlapi.obo.parser.OBOPrefix;
import org.coode.owlapi.obo.parser.OBOVocabulary;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Author: Matthew Horridge<br>
 * Stanford University<br>
 * Bio-Medical Informatics Research Group<br>
 * Date: 20/05/2012
 */
public class OBOTextEditorServiceImpl extends WebProtegeRemoteServiceServlet implements OBOTextEditorService {


    public synchronized Set<OBONamespace> getNamespaces(ProjectId projectId) {
//        if (cache == null) {
            OWLAPIProject project = getProject(projectId);
            OBONamespaceCache cache = OBONamespaceCache.createCache(project);
//        }
        return cache.getNamespaces();
    }

    public OBOTermId getTermId(ProjectId projectId, Entity entity) {
        IRI iri = IRI.create(entity.getIRI().getIRI());
        // IRI
        String id = toOBOId(iri);
        // rdfs:label
        String label = getStringAnnotationValue(projectId, iri, OWLRDFVocabulary.RDFS_LABEL.getIRI(), id);
        // namespace
        String namespace = getStringAnnotationValue(projectId, iri, OBOVocabulary.NAMESPACE.getIRI(), "");
        return new OBOTermId(id, label, namespace);
    }


    public void setTermId(ProjectId projectId, Entity entity, OBOTermId termId) {
        OWLAPIProject project = getProject(projectId);
        OBOTermId existingTermId = getTermId(projectId, entity);
        IRI iri = IRI.create(entity.getIRI().getIRI());
        List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>();
        StringBuilder description = new StringBuilder();
        if (!existingTermId.getName().equals(termId.getName())) {
            // Update label
            changes.addAll(replaceStringAnnotationValue(projectId, iri, OWLRDFVocabulary.RDFS_LABEL.getIRI(), termId.getName()));
            description.append("Set term name to ");
            description.append(termId.getName());
            description.append(" ");
        }
        if (!existingTermId.getNamespace().equals(termId.getNamespace())) {
            changes.addAll(replaceStringAnnotationValue(projectId, iri, OBOVocabulary.NAMESPACE.getIRI(), termId.getNamespace()));
            description.append("Set term namespace to ");
            description.append(termId.getNamespace());
        }
        if (!changes.isEmpty()) {
            UserId userId = getUserInSessionAndEnsureSignedIn();
            project.applyChanges(userId, changes, description.toString().trim());
        }
    }

    public List<OBOXRef> getXRefs(ProjectId projectId, Entity term) {
        OWLAPIProject project = getProject(projectId);
        IRI subject = IRI.create(term.getIRI().getIRI());
        List<OBOXRef> xrefs = new ArrayList<OBOXRef>();
        for(OWLAnnotationAssertionAxiom ax : project.getRootOntology().getAnnotationAssertionAxioms(subject)) {
            if(ax.getProperty().getIRI().equals(OBOVocabulary.XREF.getIRI())) {
                OBOXRef xref = toOBOXRef(ax.getAnnotation());
                xrefs.add(xref);
            }
        }
        return xrefs;
    }

    public void setXRefs(ProjectId projectId, Entity term, List<OBOXRef> xrefs) throws NotSignedInException {
        ensureSignedIn();
        IRI subject = IRI.create(term.getIRI().getIRI());
        Set<OWLAnnotation> annotations = convertOBOXRefsToOWLAnnotations(projectId, xrefs);
        List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>();
        OWLAPIProject project = getProject(projectId);
        OWLOntology rootOntology = project.getRootOntology();
        for(OWLAnnotation annotation : annotations) {
            OWLDataFactory df = project.getDataFactory();
            changes.add(new AddAxiom(rootOntology, df.getOWLAnnotationAssertionAxiom(subject, annotation)));
        }
        for(OWLAnnotationAssertionAxiom ax : rootOntology.getAnnotationAssertionAxioms(subject)) {
            if(ax.getProperty().getIRI().equals(OBOVocabulary.XREF.getIRI())) {
                changes.add(new RemoveAxiom(rootOntology, ax));
            }
        }
        project.applyChanges(getUserInSessionAndEnsureSignedIn(), changes, "Set XRefs");
        
    }

    private String getStringAnnotationValue(ProjectId projectId, IRI annotationSubject, IRI annotationPropertyIRI, String defaultValue) {
        OWLAPIProject project = getProject(projectId);
        OWLAnnotationAssertionAxiom labelAnnotation = null;
        for (OWLOntology ontology : project.getRootOntology().getImportsClosure()) {
            Set<OWLAnnotationAssertionAxiom> annotationAssertionAxioms = ontology.getAnnotationAssertionAxioms(annotationSubject);
            for (OWLAnnotationAssertionAxiom ax : annotationAssertionAxioms) {
                if (ax.getProperty().getIRI().equals(annotationPropertyIRI)) {
                    labelAnnotation = ax;
                    break;
                }
            }
        }

        String label = defaultValue;
        if (labelAnnotation != null) {
            label = getStringValue(labelAnnotation);
        }
        return label;
    }

    public List<OWLOntologyChange> replaceStringAnnotationValue(ProjectId projectId, IRI annotationSubject, IRI annotationPropertyIRI, String replaceWith) {
        List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>();
        OWLAPIProject project = getProject(projectId);
        OWLOntology rootOntology = project.getRootOntology();

        OWLDataFactory df = project.getDataFactory();
        OWLAnnotationProperty property = df.getOWLAnnotationProperty(annotationPropertyIRI);
        OWLLiteral value = df.getOWLLiteral(replaceWith);


        for (OWLOntology ontology : rootOntology.getImportsClosure()) {
            for (OWLAnnotationAssertionAxiom ax : ontology.getAnnotationAssertionAxioms(annotationSubject)) {
                if (ax.getProperty().getIRI().equals(annotationPropertyIRI)) {
                    changes.add(new RemoveAxiom(ontology, ax));
                    if (!replaceWith.isEmpty()) {
                        changes.add(new AddAxiom(rootOntology, df.getOWLAnnotationAssertionAxiom(property, annotationSubject, value, ax.getAnnotations())));
                    }
                }
            }
        }
        if (!replaceWith.isEmpty() && changes.isEmpty()) {
            // No previous value, so set new one
            changes.add(new AddAxiom(rootOntology, df.getOWLAnnotationAssertionAxiom(property, annotationSubject, value)));
        }
        return changes;
    }

    private String getStringValue(OWLAnnotationAssertionAxiom labelAnnotation) {
        return labelAnnotation.getValue().accept(new OWLAnnotationValueVisitorEx<String>() {
            public String visit(IRI iri) {
                return iri.toString();
            }

            public String visit(OWLAnonymousIndividual individual) {
                return individual.getID().getID();
            }

            public String visit(OWLLiteral literal) {
                return literal.getLiteral();
            }
        });
    }

    public Set<OBOTermSubset> getSubsets() {
        return null;
    }

    public void addSubset(OBOTermSubset subset) {
    }

    public void removeSubset(OBOTermSubset subset) {
    }

    public OBOTermDefinition getDefinition(ProjectId projectId, Entity term) {
        if (!(term instanceof Cls)) {
            return null;
        }
        OWLAPIProject project = getProject(projectId);
        OWLAnnotationAssertionAxiom definitionAnnotation = null;
        for (OWLOntology ontology : project.getRootOntology().getImportsClosure()) {
            Set<OWLAnnotationAssertionAxiom> annotationAssertionAxioms = ontology.getAnnotationAssertionAxioms(IRI.create(term.getIRI().getIRI()));
            for (OWLAnnotationAssertionAxiom ax : annotationAssertionAxioms) {
                if (ax.getProperty().getIRI().equals(OBOVocabulary.DEF.getIRI())) {
                    definitionAnnotation = ax;
                    break;
                }
            }
        }

        if (definitionAnnotation != null) {
            String value = definitionAnnotation.getValue().accept(new OWLAnnotationValueVisitorEx<String>() {
                public String visit(IRI iri) {
                    return iri.toString();
                }

                public String visit(OWLAnonymousIndividual individual) {
                    return individual.getID().getID();
                }

                public String visit(OWLLiteral literal) {
                    return literal.getLiteral();
                }
            });
            List<OBOXRef> xrefs = getXRefs(definitionAnnotation);
            return new OBOTermDefinition(xrefs, value);
        }
        else {
            return null;
        }
    }

    public void setDefinition(ProjectId projectId, Entity term, OBOTermDefinition definition) {
        List<OBOXRef> xRefs = definition.getXRefs();
        Set<OWLAnnotation> xrefAnnotations = convertOBOXRefsToOWLAnnotations(projectId, xRefs);

        OWLAPIProject project = getProject(projectId);
        IRI subject = IRI.create(term.getIRI().getIRI());
        OWLDataFactory df = project.getDataFactory();
        OWLAnnotationProperty defAnnotationProperty = df.getOWLAnnotationProperty(OBOVocabulary.DEF.getIRI());
        OWLLiteral defLiteral = df.getOWLLiteral(definition.getDefinition());
        OWLAnnotationAssertionAxiom definitionAssertion = df.getOWLAnnotationAssertionAxiom(defAnnotationProperty, subject, defLiteral, xrefAnnotations);

        List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>();
        OWLOntology ont = project.getRootOntology();
        for (OWLAnnotationAssertionAxiom existingAx : ont.getAnnotationAssertionAxioms(subject)) {
            if (existingAx.getProperty().getIRI().equals(OBOVocabulary.DEF.getIRI())) {
                changes.add(new RemoveAxiom(ont, existingAx));
                Set<OWLAnnotation> nonXRefAnnotations = getAxiomAnnotationsExcludingXRefs(existingAx);
                OWLAxiom fullyAnnotatedDefinitionAssertion = definitionAssertion.getAnnotatedAxiom(nonXRefAnnotations);
                changes.add(new AddAxiom(ont, fullyAnnotatedDefinitionAssertion));
            }
        }
        if (changes.isEmpty()) {
            // New
            changes.add(new AddAxiom(ont, definitionAssertion));
        }
        UserId userId = getUserInSessionAndEnsureSignedIn();
        project.applyChanges(userId, changes, "Set term definition");
    }

    private Set<OWLAnnotation> getAxiomAnnotationsExcludingXRefs(OWLAnnotationAssertionAxiom existingAx) {
        Set<OWLAnnotation> annotationsToCopy = new HashSet<OWLAnnotation>();
        for (OWLAnnotation existingAnnotation : existingAx.getAnnotations()) {
            if (!existingAnnotation.getProperty().getIRI().equals(OBOVocabulary.XREF.getIRI())) {
                annotationsToCopy.add(existingAnnotation);
            }
        }
        return annotationsToCopy;
    }

    private Set<OWLAnnotation> convertOBOXRefsToOWLAnnotations(ProjectId projectId, List<OBOXRef> xRefs) {
        Set<OWLAnnotation> xrefAnnotations = new HashSet<OWLAnnotation>();
        for (OBOXRef xref : xRefs) {
            if (!xref.isEmpty()) {
                OWLAnnotation xrefAnnotation = convertXRefToAnnotation(projectId, xref);
                xrefAnnotations.add(xrefAnnotation);
            }
        }
        return xrefAnnotations;
    }

    private String escapeSpaces(String s) {
        return s.replace(" ", "%20");
    }
    
    
    private OWLAnnotation convertXRefToAnnotation(ProjectId projectId, OBOXRef xref) {
        OWLAPIProject project = getProject(projectId);
        OWLDataFactory df = project.getDataFactory();
        OWLAnnotationProperty xrefAnnotationProperty = df.getOWLAnnotationProperty(OBOVocabulary.XREF.getIRI());
        String oboId = xref.toOBOId();
        String escapedId = escapeSpaces(oboId);
        OBOIdType type = OBOIdType.getIdType(escapedId);
        IRI xrefIRIValue = type.getIRIFromOBOId(project.getRootOntology().getOntologyID(), new IDSpaceManager(), escapedId);
        Set<OWLAnnotation> descriptionAnnotations;
        if (xref.getDescription().isEmpty()) {
            descriptionAnnotations = Collections.emptySet();
        }
        else {
            OWLAnnotation descriptionAnnotation = df.getOWLAnnotation(df.getRDFSComment(), df.getOWLLiteral(xref.getDescription()));
            descriptionAnnotations = Collections.singleton(descriptionAnnotation);
        }
        return df.getOWLAnnotation(xrefAnnotationProperty, xrefIRIValue, descriptionAnnotations);
    }

    private List<OBOXRef> getXRefs(OWLAnnotationAssertionAxiom annotationAssertion) {
        List<OBOXRef> result = new ArrayList<OBOXRef>();
        for (OWLAnnotation annotation : annotationAssertion.getAnnotations()) {
            if (annotation.getProperty().getIRI().equals(OBOVocabulary.XREF.getIRI())) {
                OBOXRef oboxRef = toOBOXRef(annotation);
                if (oboxRef != null) {
                    result.add(oboxRef);
                }
            }
        }
        return result;
    }

    private OBOXRef toOBOXRef(final OWLAnnotation annotation) {

        return annotation.getValue().accept(new OWLAnnotationValueVisitorEx<OBOXRef>() {
            public OBOXRef visit(IRI iri) {
                String description = "";
                for (OWLAnnotation anno : annotation.getAnnotations()) {
                    if (anno.getProperty().isComment()) {
                        description = anno.getValue().accept(new OWLAnnotationValueVisitorEx<String>() {
                            public String visit(IRI iri) {
                                return iri.toString();
                            }

                            public String visit(OWLAnonymousIndividual individual) {
                                return individual.toString();
                            }

                            public String visit(OWLLiteral literal) {
                                return literal.getLiteral();
                            }
                        });
                        break;
                    }
                }
                return toOBOXRef(iri, description);
            }

            public OBOXRef visit(OWLAnonymousIndividual individual) {
                return null;
            }

            public OBOXRef visit(OWLLiteral literal) {
                return null;
            }
        });
    }

    private static Pattern SEPARATOR_PATTERN = Pattern.compile("([^#_|_]+)(#_|_)(.+)");

    private OBOXRef toOBOXRef(IRI xrefValue, String description) {
        // Need to peel apart the ID
        String value = xrefValue.toString();
        if (value.startsWith(OBOPrefix.OBO.getPrefix())) {
            String localValue = value.substring(OBOPrefix.OBO.getPrefix().length());
            Matcher matcher = SEPARATOR_PATTERN.matcher(localValue);
            if (matcher.matches()) {
                String dbname = unescapeSpaces(matcher.group(1));
                String dbid = matcher.group(3);
                return new OBOXRef(dbname, dbid, description);
            }
            else {
                return null;
            }
        }
        else {
            return null;
        }
    }
    
    private String unescapeSpaces(String s) {
        if(s == null) {
            return "";
        }
        return s.replace("%20", " ");
    } 
    

    private String toOBOId(IRI iri) {
        String value = iri.toString();
        String localPart = "";
        if (value.startsWith(OBOPrefix.OBO.getPrefix())) {
            localPart = value.substring(OBOPrefix.OBO.getPrefix().length());
        }
        else if (value.startsWith(OBOPrefix.OBO_IN_OWL.getPrefix())) {
            localPart = value.substring(OBOPrefix.OBO_IN_OWL.getPrefix().length());

        }
        else if (value.startsWith(OBOPrefix.IAO.getPrefix())) {
            localPart = value.substring(OBOPrefix.IAO.getPrefix().length());
        }
        else {
            String fragment = iri.getFragment();
            if (fragment != null) {
                localPart = fragment;
            }
            else {
                localPart = value;
            }
        }
        Matcher matcher = SEPARATOR_PATTERN.matcher(localPart);
        if (matcher.matches()) {
            StringBuilder sb = new StringBuilder();
            sb.append(matcher.group(1));
            sb.append(":");
            sb.append(matcher.group(3));
            return sb.toString();
        }
        else {
            return value;
        }
    }

    private OWLAPIProject getProject(ProjectId projectId) {
        OWLAPIProjectManager pm = OWLAPIProjectManager.getProjectManager();
        return pm.getProject(projectId);
    }

    public Collection<OBOTermSynonym> getSynonyms(ProjectId projectId, Entity term) {
        Set<OBOTermSynonym> result = new HashSet<OBOTermSynonym>();
        OWLAPIProject project = getProject(projectId);
        for (OWLOntology ontology : project.getRootOntology().getImportsClosure()) {
            Set<OWLAnnotationAssertionAxiom> annotationAssertionAxioms = ontology.getAnnotationAssertionAxioms(IRI.create(term.getIRI().getIRI()));
            for (OWLAnnotationAssertionAxiom ax : annotationAssertionAxioms) {
                OBOTermSynonymScope synonymScope = getSynonymScope(ax);
                if (synonymScope != null) {
                    OBOTermSynonym termSynonym = new OBOTermSynonym(getXRefs(ax), getStringValue(ax), synonymScope);
                    result.add(termSynonym);
                }
            }
        }

        return result;
    }

    public void setSynonyms(ProjectId projectId, Entity term, Collection<OBOTermSynonym> synonyms) throws NotSignedInException {
        IRI subject = IRI.create(term.getIRI().getIRI());
        OWLAPIProject project = getProject(projectId);
        OWLOntology rootOntology = project.getRootOntology();
        OWLDataFactory df = project.getDataFactory();
        List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>();
        for (OBOTermSynonym synonym : synonyms) {
            OWLAnnotationProperty synonymProperty = getSynonymAnnoationProperty(df, synonym.getScope());
            OWLLiteral synonymNameLiteral = df.getOWLLiteral(synonym.getName());
            Set<OWLAnnotation> synonymXRefs = convertOBOXRefsToOWLAnnotations(projectId, synonym.getXRefs());
            OWLAnnotationAssertionAxiom synonymAnnotationAssertion = df.getOWLAnnotationAssertionAxiom(synonymProperty, subject, synonymNameLiteral, synonymXRefs);
            changes.add(new AddAxiom(rootOntology, synonymAnnotationAssertion));
        }

        for (OWLAnnotationAssertionAxiom ax : project.getRootOntology().getAnnotationAssertionAxioms(subject)) {
            if (getSynonymScope(ax) != null) {
                changes.add(new RemoveAxiom(rootOntology, ax));
            }
        }
        project.applyChanges(getUserInSessionAndEnsureSignedIn(), changes, "Set synonym");
    }

    public OWLAnnotationProperty getSynonymAnnoationProperty(OWLDataFactory df, OBOTermSynonymScope scope) {
        switch (scope) {
            case EXACT:
                return df.getOWLAnnotationProperty(OBOVocabulary.EXACT_SYNONYM.getIRI());
            case NARROWER:
                return df.getOWLAnnotationProperty(OBOVocabulary.NARROW_SYNONYM.getIRI());
            case BROADER:
                return df.getOWLAnnotationProperty(OBOVocabulary.BROAD_SYNONYM.getIRI());
            case RELATED:
                return df.getOWLAnnotationProperty(OBOVocabulary.RELATED_SYNONYM.getIRI());
            default:
                throw new RuntimeException("Unknown synonym scope: " + scope);
        }
    }


    public OBOTermSynonymScope getSynonymScope(OWLAnnotationAssertionAxiom ax) {
        IRI iri = ax.getProperty().getIRI();
        if (iri.equals(OBOVocabulary.EXACT_SYNONYM.getIRI())) {
            return OBOTermSynonymScope.EXACT;
        }
        else if (iri.equals(OBOVocabulary.RELATED_SYNONYM.getIRI())) {
            return OBOTermSynonymScope.RELATED;
        }
        else if (iri.equals(OBOVocabulary.NARROW_SYNONYM.getIRI())) {
            return OBOTermSynonymScope.NARROWER;
        }
        else if (iri.equals(OBOVocabulary.BROAD_SYNONYM.getIRI())) {
            return OBOTermSynonymScope.BROADER;
        }
        else {
            return null;
        }

    }

    public OBOTermRelationships getRelationships(ProjectId projectId, Cls term) {
        OWLAPIProject project = getProject(projectId);
        OWLClass cls = project.getDataFactory().getOWLClass(IRI.create(term.getIRI().getIRI()));
        Set<OWLSubClassOfAxiom> subClassOfAxioms = project.getRootOntology().getSubClassAxiomsForSubClass(cls);
        Set<OBORelationship> rels = new HashSet<OBORelationship>();
        for (OWLSubClassOfAxiom ax : subClassOfAxioms) {
            Set<OWLObjectSomeValuesFrom> relationships = new HashSet<OWLObjectSomeValuesFrom>();
            Set<OWLClassExpression> conjuncts = ax.getSuperClass().asConjunctSet();
            for (OWLClassExpression conjunct : conjuncts) {
                if (conjunct instanceof OWLObjectSomeValuesFrom) {
                    OWLObjectSomeValuesFrom svf = (OWLObjectSomeValuesFrom) conjunct;
                    if (!svf.getProperty().isAnonymous() && !svf.getFiller().isAnonymous()) {
                        relationships.add((OWLObjectSomeValuesFrom) conjunct);
                    }
                }
            }
            if (relationships.size() == conjuncts.size()) {
                for (OWLObjectSomeValuesFrom rel : relationships) {
                    VisualObjectProperty property = toVisualObjectProperty(rel.getProperty().asOWLObjectProperty(), project);
                    VisualCls filler = toVisualCls(rel.getFiller().asOWLClass(), project);
                    OBORelationship oboRel = new OBORelationship(property, filler);
                    rels.add(oboRel);
                }
            }
        }
        return new OBOTermRelationships(rels);
    }

    public void setRelationships(ProjectId projectId, Cls lastEntity, OBOTermRelationships relationships) {
        ensureSignedIn();
        if (relationships == null) {
            throw new NullPointerException("relationships must not be null");
        }
        OWLAPIProject project = getProject(projectId);

        OWLDataFactory dataFactory = project.getDataFactory();

        Set<OWLObjectSomeValuesFrom> superClsesToSet = new HashSet<OWLObjectSomeValuesFrom>();
        for (OBORelationship relationship : relationships.getRelationships()) {
            OWLObjectSomeValuesFrom someValuesFrom = toSomeValuesFrom(dataFactory, relationship);
            superClsesToSet.add(someValuesFrom);
        }


        List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>();

        OWLOntology ontology = project.getRootOntology();
        OWLClass cls = dataFactory.getOWLClass(IRI.create(lastEntity.getIRI().getIRI()));
        Set<OWLObjectSomeValuesFrom> existingSuperClsesToReplace = new HashSet<OWLObjectSomeValuesFrom>();
        for (OWLSubClassOfAxiom ax : ontology.getSubClassAxiomsForSubClass(cls)) {
            if (ax.getSuperClass() instanceof OWLObjectSomeValuesFrom) {
                OWLObjectSomeValuesFrom existing = (OWLObjectSomeValuesFrom) ax.getSuperClass();
                existingSuperClsesToReplace.add(existing);
            }
        }
        // What's changed?

        StringBuilder description = new StringBuilder();
        for (OWLObjectSomeValuesFrom toReplace : existingSuperClsesToReplace) {
            if (!superClsesToSet.contains(toReplace)) {
                // Was there but not any longer
                changes.add(new RemoveAxiom(ontology, dataFactory.getOWLSubClassOfAxiom(cls, toReplace)));
                description.append("Removed ");
                description.append(project.getRenderingManager().getBrowserText(toReplace.getProperty()));
                description.append(" relationship to ");
                description.append(project.getRenderingManager().getBrowserText(toReplace.getFiller()));
                description.append("    ");
            }
        }
        // What do we add?
        for (OWLObjectSomeValuesFrom toSet : superClsesToSet) {
            if (!existingSuperClsesToReplace.contains(toSet)) {
                // Not already there - we're adding it.
                changes.add(new AddAxiom(ontology, dataFactory.getOWLSubClassOfAxiom(cls, toSet)));
                description.append("Added ");
                description.append(project.getRenderingManager().getBrowserText(toSet.getProperty()));
                description.append(" relationship to ");
                description.append(project.getRenderingManager().getBrowserText(toSet.getFiller()));
                description.append("    ");
            }
        }


        if (!changes.isEmpty()) {
            UserId userId = getUserInSessionAndEnsureSignedIn();
            project.applyChanges(userId, changes, "Edited relationship values: " + description.toString());
        }

    }

    private OWLObjectSomeValuesFrom toSomeValuesFrom(OWLDataFactory dataFactory, OBORelationship relationship) {
        ObjectProperty property = relationship.getRelation().getObject();
        OWLObjectProperty owlObjectProperty = dataFactory.getOWLObjectProperty(IRI.create(property.getIRI().getIRI()));
        Cls filler = relationship.getValue().getObject();
        OWLClass owlCls = dataFactory.getOWLClass(IRI.create(filler.getIRI().getIRI()));
        return dataFactory.getOWLObjectSomeValuesFrom(owlObjectProperty, owlCls);
    }

    public OBOTermCrossProduct getCrossProduct(ProjectId projectId, Cls term) {
        OWLAPIProject project = getProject(projectId);
        OWLDataFactory df = project.getDataFactory();
        OWLClass cls = toOWLClass(df, term);
        OWLEquivalentClassesAxiom axiom = getCrossProductEquivalentClassesAxiom(project.getRootOntology(), cls);
        if (axiom == null) {
            return OBOTermCrossProduct.emptyOBOTermCrossProduct();
        }

        Set<OWLObjectSomeValuesFrom> relationships = new HashSet<OWLObjectSomeValuesFrom>();
        OWLClass genus = null;
        for (OWLClassExpression operand : axiom.getClassExpressionsMinus(cls)) {
            Set<OWLClassExpression> conjuncts = operand.asConjunctSet();
            for (OWLClassExpression conjunct : conjuncts) {
                if (conjunct instanceof OWLObjectSomeValuesFrom) {
                    OWLObjectSomeValuesFrom svf = (OWLObjectSomeValuesFrom) conjunct;
                    if (!svf.getProperty().isAnonymous() && !svf.getFiller().isAnonymous()) {
                        relationships.add((OWLObjectSomeValuesFrom) conjunct);
                    }
                }
                else if (conjunct instanceof OWLClass) {
                    genus = (OWLClass) conjunct;
                }
            }
        }
        Set<OBORelationship> discriminatingRelationships = new HashSet<OBORelationship>();
        VisualCls visualCls = null;
        if (genus != null) {
            visualCls = toVisualCls(genus, project);
        }

        for (OWLObjectSomeValuesFrom rel : relationships) {
            VisualObjectProperty property = toVisualObjectProperty(rel.getProperty().asOWLObjectProperty(), project);
            VisualCls filler = toVisualCls(rel.getFiller().asOWLClass(), project);
            OBORelationship oboRel = new OBORelationship(property, filler);
            discriminatingRelationships.add(oboRel);
        }
        return new OBOTermCrossProduct(visualCls, new OBOTermRelationships(discriminatingRelationships));

    }

    /**
     * Gets an equivalent classes axiom that corresponds to an OBO Cross Product.  An equivalent classes axiom AX
     * corresponds to a cross product for a class C if AX contains C as an operand, and AX contains one other class
     * which is either an ObjectSomeValuesFrom restriction, or an intersection of ObjectSomeValuesFrom restrictions
     * plus an optional named class.  i.e.   AX = EquivalentClasses(C ObjectIntersectionOf(A ObjectSomeValuesFrom(..)...
     * ObjectSomeValuesFrom(..))
     * @param ontology The ontology in which to search
     * @param cls The subject of the cross product
     * @return An {@link OWLEquivalentClassesAxiom} that corresponds to a cross product for the class, or
     * <code>null</code> if the ontology doesn't contain an equivalent classes axiom that corresponds to a cross
     * product.
     */
    public OWLEquivalentClassesAxiom getCrossProductEquivalentClassesAxiom(OWLOntology ontology, OWLClass cls) {
        Set<OWLEquivalentClassesAxiom> candidates = new TreeSet<OWLEquivalentClassesAxiom>();
        for (OWLEquivalentClassesAxiom axiom : ontology.getEquivalentClassesAxioms(cls)) {
            Set<OWLClassExpression> equivalentClasses = axiom.getClassExpressionsMinus(cls);
            int namedCount = 0;
            int someValuesFromCount = 0;
            int otherCount = 0;
            for (OWLClassExpression operand : equivalentClasses) {
                for (OWLClassExpression ce : operand.asConjunctSet()) {
                    if (ce instanceof OWLClass) {
                        namedCount++;
                    }
                    else if (ce instanceof OWLObjectSomeValuesFrom) {
                        OWLObjectSomeValuesFrom svf = (OWLObjectSomeValuesFrom) ce;
                        if (!svf.getProperty().isAnonymous() && !svf.getFiller().isAnonymous()) {
                            someValuesFromCount++;
                        }
                    }
                    else {
                        otherCount++;
                    }
                }
            }
            if (namedCount <= 1 && someValuesFromCount > 0 && otherCount == 0) {
                candidates.add(axiom.getAxiomWithoutAnnotations());
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() == 1) {
            return candidates.iterator().next();
        }
        // More than one
        // Return the first one (they are ordered by the OWLObject comparator, so for a given set of class expression this
        // is consistent
        return candidates.iterator().next();
    }


    public void setCrossProduct(ProjectId projectId, Cls term, OBOTermCrossProduct crossProduct) throws NotSignedInException {
        if(crossProduct == null) {
            throw new RuntimeException("crossProduct must not be null");
        }

        OWLAPIProject project = getProject(projectId);
        OWLDataFactory df = project.getDataFactory();

        Set<OWLClassExpression> intersectionOperands = new HashSet<OWLClassExpression>();

        VisualCls visualGenus = crossProduct.getGenus();
        if (visualGenus != null) {
            OWLClass cls = toOWLClass(df, visualGenus.getObject());
            intersectionOperands.add(cls);
        }

        for (OBORelationship relationship : crossProduct.getRelationships().getRelationships()) {
            OWLObjectSomeValuesFrom someValuesFrom = toSomeValuesFrom(df, relationship);
            intersectionOperands.add(someValuesFrom);
        }
        OWLObjectIntersectionOf intersectionOf = df.getOWLObjectIntersectionOf(intersectionOperands);

        OWLClass owlClass = toOWLClass(df, term);
        OWLEquivalentClassesAxiom newXPAxiom = df.getOWLEquivalentClassesAxiom(owlClass, intersectionOf);

        OWLOntology rootOntology = project.getRootOntology();
        OWLEquivalentClassesAxiom existingXPAxiom = getCrossProductEquivalentClassesAxiom(rootOntology, owlClass);
        
        UserId userId = getUserInSessionAndEnsureSignedIn();
        List<OWLOntologyChange> changes = new ArrayList<OWLOntologyChange>();
        changes.add(new AddAxiom(rootOntology, newXPAxiom));
        if (existingXPAxiom != null) {
            changes.add(new RemoveAxiom(rootOntology, existingXPAxiom));
        }
        project.applyChanges(userId, changes, "Set cross product values");
        
    }

    private OWLClass toOWLClass(OWLDataFactory dataFactory, Cls cls) {
        return dataFactory.getOWLClass(IRI.create(cls.getIRI().getIRI()));
    }


    private VisualCls toVisualCls(OWLClass cls, OWLAPIProject project) {
        return new VisualCls(new Cls(new WebProtegeIRI(cls.getIRI().toString())), project.getRenderingManager().getBrowserText(cls));
    }

    private VisualObjectProperty toVisualObjectProperty(OWLObjectProperty property, OWLAPIProject project) {
        return new VisualObjectProperty(new ObjectProperty(new WebProtegeIRI(property.getIRI().toString())), project.getRenderingManager().getBrowserText(property));
    }

}
