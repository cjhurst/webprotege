package edu.stanford.bmir.protege.web.shared.frame;

import edu.stanford.bmir.protege.web.shared.entity.OWLObjectPropertyData;
import edu.stanford.bmir.protege.web.shared.project.ProjectId;

/**
 * Author: Matthew Horridge<br>
 * Stanford University<br>
 * Bio-Medical Informatics Research Group<br>
 * Date: 23/04/2013
 */
public class UpdateObjectPropertyFrameAction extends UpdateFrameAction<ObjectPropertyFrame, OWLObjectPropertyData> {

    /**
     * For serialization purposes only
     */
    private UpdateObjectPropertyFrameAction() {
    }

    public UpdateObjectPropertyFrameAction(ProjectId projectId, LabelledFrame<ObjectPropertyFrame> from, LabelledFrame<ObjectPropertyFrame> to) {
        super(projectId, from, to);
    }
}
