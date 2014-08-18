package svnserver.config;

import org.jetbrains.annotations.NotNull;

/**
 * Repository configuration.
 *
 * @author a.navrotskiy
 */
public class RepositoryConfig {
  @NotNull
  private String branch = "master";
  @NotNull
  private String path = ".git";
  @NotNull
  private String[] linked = {};

  @NotNull
  public String getBranch() {
    return branch;
  }

  public void setBranch(@NotNull String branch) {
    this.branch = branch;
  }

  @NotNull
  public String getPath() {
    return path;
  }

  public void setPath(@NotNull String path) {
    this.path = path;
  }

  public void setLinked(@NotNull String[] linked) {
    this.linked = linked;
  }

  @NotNull
  public String[] getLinked() {
    return linked;
  }
}
