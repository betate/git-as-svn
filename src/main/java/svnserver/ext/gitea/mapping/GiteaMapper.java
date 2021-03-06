/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitea.mapping;

import io.gitea.ApiClient;
import io.gitea.ApiException;
import io.gitea.api.RepositoryApi;
import io.gitea.model.Repository;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.tmatesoft.svn.core.SVNException;
import svnserver.Loggers;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;

final class GiteaMapper extends Thread implements DirectoryWatcher.DirectoryMapping {
  @NotNull
  private static final Logger log = Loggers.gitea;

  @NotNull
  private final LinkedList<String> toAdd = new LinkedList<>();
  @NotNull
  private final LinkedList<String> toRemove = new LinkedList<>();
  @NotNull
  private final GiteaMapping mapping;
  @NotNull
  private final RepositoryApi repositoryApi;

  GiteaMapper(@NotNull ApiClient apiClient, @NotNull GiteaMapping mapping) {
    this.repositoryApi = new RepositoryApi(apiClient);
    this.mapping = mapping;
    this.start();
  }

  public void run() {
    try {
      while (true) {
        synchronized (toAdd) {
          for (Iterator<String> it = toRemove.iterator(); it.hasNext(); ) {
            String projectName = it.next();
            mapping.removeRepository(projectName);
            it.remove();
          }
          for (Iterator<String> it = toAdd.iterator(); it.hasNext(); ) {
            String projectName = it.next();
            String owner = projectName.substring(0, projectName.indexOf('/'));
            String repo = projectName.substring(projectName.indexOf('/') + 1);
            try {
              Repository repository = repositoryApi.repoGet(owner, repo);
              GiteaProject project = mapping.addRepository(repository);
              if (project != null) {
                project.initRevisions();
              }
              it.remove();
            } catch (ApiException e) {
              // Not ready yet - try again later...
            } catch (IOException | SVNException e) {
              log.error("Processing error whilst adding repository: {} / {}: {}", owner, repo, e.getMessage());
            }
          }
        }
        sleep(1000);
      }
    } catch (InterruptedException e) {
      // noop
    }
  }

  @Override
  public void addRepository(@NotNull String owner, @NotNull String repo) {
    synchronized (toAdd) {
      toAdd.add(owner + "/" + repo);
    }
  }

  @Override
  public void removeRepositories(@NotNull String owner) {
    synchronized (toAdd) {
      for (GiteaProject project : mapping.getMapping().values()) {
        if (project.getOwner().equals(owner)) {
          toRemove.add(project.getRepositoryName());
          toAdd.remove(project.getRepositoryName());
        }
      }
    }
  }

  @Override
  public void removeRepository(@NotNull String owner, @NotNull String repo) {
    synchronized (toAdd) {
      toRemove.add(owner + "/" + repo);
      toAdd.remove(owner + "/" + repo);
    }
  }
}