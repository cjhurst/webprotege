package edu.stanford.bmir.protege.web.client.rpc;

import com.google.gwt.user.client.rpc.AsyncCallback;
import edu.stanford.bmir.protege.web.client.rpc.data.*;
import edu.stanford.bmir.protege.web.client.rpc.data.NewProjectSettings;

import java.util.List;
import java.util.Set;

public interface ProjectManagerServiceAsync {

    void getProjects(AsyncCallback<List<String>> async);

    void getProjects(UserId projectOwner, AsyncCallback<List<ProjectData>> async);

    void isRegisteredProject(ProjectId projectId, AsyncCallback<Boolean> async);

    /**
     * Creates a new project.  The project is initialised based on the {@link edu.stanford.bmir.protege.web.client.rpc.data.NewProjectSettings}
     * parameter.
     * @param newProjectSettings A {@link edu.stanford.bmir.protege.web.client.rpc.data.NewProjectSettings} object that
     * specifies the intended owner of the project,
     * the project name, a description for the project, sources etc. etc.
     * @throws edu.stanford.bmir.protege.web.client.rpc.data.ProjectAlreadyExistsException
     *          if the project already exists on the server.
     */
    void createNewProject(NewProjectSettings newProjectSettings, AsyncCallback<Void> async);

    /**
     * Gets a list of all project names.
     * @return A list of strings representing the names of project.
     */
    void getProjectNames(AsyncCallback<List<String>> async);

    /**
     * Removes a project from the trash.
     * @param projectIds
     */
    void removeProjectsFromTrash(Set<ProjectId> projectIds, AsyncCallback<Void> async);

    /**
     * Moves a set of projects to the trash.
     * @param projectIds The projects to move to the trash.  Not null.
     * @throws NullPointerException if projectIds is null.
     */
    void moveProjectsToTrash(Set<ProjectId> projectIds, AsyncCallback<Void> async);

    /**
     * Gets a list of project names for which the signed in user is the owner.
     * @return A list of project names for which the signed in user is the owner.  This list will be empty if the user
     *         is not signed in.
     */
    void getOwnedProjectNames(AsyncCallback<List<String>> async);

//    /**
//     * Overwrites the project document for an existing project.
//     * @param newProjectSettings The settings that describe both a currently registered project and how to overwrite
//     * the
//     * current project document.
//     * @throws edu.stanford.bmir.protege.web.client.rpc.data.NotSignedInException
//     *          If the caller is not signed in.
//     * @throws edu.stanford.bmir.protege.web.client.rpc.data.ProjectNotRegisteredException
//     *          If the NewProjectSettings object does not refer to a project that is already
//     *          registered.  The {@link edu.stanford.bmir.protege.web.client.rpc.data.NewProjectSettings#getProjectName()}
//     *          must
//     *          match a the name of a registered project.
//     * @throws edu.stanford.bmir.protege.web.client.rpc.data.NotProjectOwnerException
//     *          If the caller is not the owner of the existing registered project.  Note that
//     *          there is no requirement for the caller to be the owner of the final overwritten project (i.e. this
//     *          method supports
//     *          a change in ownership).
//     */
//    void replaceProjectDocument(NewProjectSettings newProjectSettings, AsyncCallback<Void> async);

    void getLastAccessTime(ProjectId projectId, AsyncCallback<Long> async);
}
