/**
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.Assert;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnCheckout;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.tmatesoft.svn.core.wc2.SvnUpdate;
import svnserver.TestHelper;
import svnserver.replay.ReportSVNEditor;
import svnserver.tester.SvnTester;
import svnserver.tester.SvnTesterDataProvider;
import svnserver.tester.SvnTesterExternalListener;
import svnserver.tester.SvnTesterFactory;

import java.io.File;

import static svnserver.SvnTestHelper.sendDeltaAndClose;

/**
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
@Listeners(SvnTesterExternalListener.class)
public final class DepthTest {

  @NotNull
  private SvnTester server;
  @NotNull
  private SvnOperationFactory factory;
  @NotNull
  private File wc;

  @NotNull
  private SvnTester create(@NotNull SvnTesterFactory factory) throws Exception {
    final SvnTester tester = factory.create();

    final SVNRepository repo = tester.openSvnRepository();
    final ISVNEditor editor = repo.getCommitEditor("", null);
    editor.openRoot(-1);
    editor.addDir("/a", null, -1);
    editor.addDir("/a/b", null, -1);

    editor.addFile("/a/b/e", null, -1);
    sendDeltaAndClose(editor, "/a/b/e", null, "e body");

    editor.addDir("/a/b/c", null, -1);

    editor.addFile("/a/b/c/d", null, -1);
    sendDeltaAndClose(editor, "/a/b/c/d", null, "d body");

    editor.closeDir();
    editor.closeDir();
    editor.closeDir();
    editor.closeDir();
    editor.closeEdit();

    this.server = tester;
    this.wc = TestHelper.createTempDir("git-as-svn-wc");
    this.factory = new SvnOperationFactory();
    this.factory.setOptions(new DefaultSVNOptions(this.wc, true));
    ISVNAuthenticationManager auth = server.openSvnRepository().getAuthenticationManager();
    if (auth != null) {
      this.factory.setAuthenticationManager(auth);
    }

    return tester;
  }

  private void checkout(@NotNull String path, @NotNull SVNDepth depth) throws SVNException {
    final SvnCheckout checkout = factory.createCheckout();
    checkout.setSource(SvnTarget.fromURL(server.getUrl().appendPath(path, true)));
    checkout.setSingleTarget(SvnTarget.fromFile(wc));
    checkout.setRevision(SVNRevision.HEAD);
    checkout.setDepth(depth);
    checkout.run();
  }

  private void update(@NotNull String path, @Nullable SVNDepth depth) throws SVNException {
    final SvnUpdate update = factory.createUpdate();
    update.setSingleTarget(SvnTarget.fromFile(new File(wc, path)));
    update.setRevision(SVNRevision.HEAD);

    if (depth != null) {
      update.setDepthIsSticky(true);
      update.setDepth(depth);
    }

    update.run();
  }

  @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider.class)
  public void empty(@NotNull SvnTesterFactory factory) throws Exception {
    try (SvnTester server = create(factory)) {
      final long revision = server.openSvnRepository().getLatestRevision();
      // svn checkout --depth empty
      check(server, "", SVNDepth.EMPTY, reporter -> {
        reporter.setPath("", null, revision, SVNDepth.EMPTY, true);
        reporter.finishReport();
      }, " - open-root: r0\n" +
          "/ - change-dir-prop: svn:entry:committed-date\n" +
          "/ - change-dir-prop: svn:entry:committed-rev\n" +
          "/ - change-dir-prop: svn:entry:last-author\n" +
          "/ - change-dir-prop: svn:entry:uuid\n");

      // svn update
      check(server, "", SVNDepth.EMPTY, reporter -> {
        reporter.setPath("", null, revision, SVNDepth.EMPTY, false);
        reporter.finishReport();
      }, " - open-root: r0\n" +
          "/ - change-dir-prop: svn:entry:committed-date\n" +
          "/ - change-dir-prop: svn:entry:committed-rev\n" +
          "/ - change-dir-prop: svn:entry:last-author\n" +
          "/ - change-dir-prop: svn:entry:uuid\n");

      // svn update --set-depth infinity
      check(server, "", SVNDepth.INFINITY, reporter -> {
        reporter.setPath("", null, revision, SVNDepth.EMPTY, false);
        reporter.finishReport();
      }, " - open-root: r0\n" +
          "/ - add-dir\n" +
          "/ - add-file\n" +
          "/ - change-dir-prop: svn:entry:committed-date\n" +
          "/ - change-dir-prop: svn:entry:committed-rev\n" +
          "/ - change-dir-prop: svn:entry:last-author\n" +
          "/ - change-dir-prop: svn:entry:uuid\n" +
          "a/b/c/d - apply-text-delta: null\n" +
          "a/b/c/d - change-file-prop: svn:entry:committed-date\n" +
          "a/b/c/d - change-file-prop: svn:entry:committed-rev\n" +
          "a/b/c/d - change-file-prop: svn:entry:last-author\n" +
          "a/b/c/d - change-file-prop: svn:entry:uuid\n" +
          "a/b/c/d - close-file: e08b5cff98d6e3f8a892fc999622d441\n" +
          "a/b/c/d - delta-chunk\n" +
          "a/b/c/d - delta-end\n" +
          "a/b/e - apply-text-delta: null\n" +
          "a/b/e - change-file-prop: svn:entry:committed-date\n" +
          "a/b/e - change-file-prop: svn:entry:committed-rev\n" +
          "a/b/e - change-file-prop: svn:entry:last-author\n" +
          "a/b/e - change-file-prop: svn:entry:uuid\n" +
          "a/b/e - close-file: babc2f91dac8ef35815e635d89196696\n" +
          "a/b/e - delta-chunk\n" +
          "a/b/e - delta-end\n");
    }
  }

  @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider.class)
  public void emptySubdir(@NotNull SvnTesterFactory factory) throws Exception {
    try (SvnTester server = create(factory)) {
      checkout("a/b", SVNDepth.EMPTY);
      Assert.assertFalse(new File(wc, "c").exists());
      Assert.assertFalse(new File(wc, "e").exists());

      update("", null);
      Assert.assertFalse(new File(wc, "c").exists());
      Assert.assertFalse(new File(wc, "e").exists());

      update("", SVNDepth.INFINITY);
      Assert.assertTrue(new File(wc, "c/d").exists());
      Assert.assertTrue(new File(wc, "e").exists());
    }
  }

  @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider.class)
  public void emptySubdir2(@NotNull SvnTesterFactory factory) throws Exception {
    try (SvnTester server = create(factory)) {
      checkout("a/b/c", SVNDepth.EMPTY);
      Assert.assertFalse(new File(wc, "d").exists());

      update("", null);
      Assert.assertFalse(new File(wc, "d").exists());

      update("", SVNDepth.INFINITY);
      Assert.assertTrue(new File(wc, "d").exists());
    }
  }

  @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider.class)
  public void infinity(@NotNull SvnTesterFactory factory) throws Exception {
    try (SvnTester server = create(factory)) {
      final long revision = server.openSvnRepository().getLatestRevision();
      // svn checkout --depth infinity a
      check(server, "", SVNDepth.INFINITY, reporter -> {
        reporter.setPath("", null, revision, SVNDepth.INFINITY, true);
        reporter.finishReport();
      }, " - open-root: r0\n" +
          "/ - add-dir\n" +
          "/ - add-file\n" +
          "/ - change-dir-prop: svn:entry:committed-date\n" +
          "/ - change-dir-prop: svn:entry:committed-rev\n" +
          "/ - change-dir-prop: svn:entry:last-author\n" +
          "/ - change-dir-prop: svn:entry:uuid\n" +
          "a/b/c/d - apply-text-delta: null\n" +
          "a/b/c/d - change-file-prop: svn:entry:committed-date\n" +
          "a/b/c/d - change-file-prop: svn:entry:committed-rev\n" +
          "a/b/c/d - change-file-prop: svn:entry:last-author\n" +
          "a/b/c/d - change-file-prop: svn:entry:uuid\n" +
          "a/b/c/d - close-file: e08b5cff98d6e3f8a892fc999622d441\n" +
          "a/b/c/d - delta-chunk\n" +
          "a/b/c/d - delta-end\n" +
          "a/b/e - apply-text-delta: null\n" +
          "a/b/e - change-file-prop: svn:entry:committed-date\n" +
          "a/b/e - change-file-prop: svn:entry:committed-rev\n" +
          "a/b/e - change-file-prop: svn:entry:last-author\n" +
          "a/b/e - change-file-prop: svn:entry:uuid\n" +
          "a/b/e - close-file: babc2f91dac8ef35815e635d89196696\n" +
          "a/b/e - delta-chunk\n" +
          "a/b/e - delta-end\n");

      // svn update
      check(server, "", SVNDepth.UNKNOWN, reporter -> {
        reporter.setPath("", null, revision, SVNDepth.INFINITY, false);
        reporter.finishReport();
      }, " - open-root: r0\n" +
          "/ - change-dir-prop: svn:entry:committed-date\n" +
          "/ - change-dir-prop: svn:entry:committed-rev\n" +
          "/ - change-dir-prop: svn:entry:last-author\n" +
          "/ - change-dir-prop: svn:entry:uuid\n");
    }
  }

  @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider.class)
  public void infinitySubdir(@NotNull SvnTesterFactory factory) throws Exception {
    try (SvnTester server = create(factory)) {
      final long revision = server.openSvnRepository().getLatestRevision();
      // svn checkout --depth infinity a
      check(server, "a", SVNDepth.INFINITY, reporter -> {
        reporter.setPath("", null, revision, SVNDepth.INFINITY, true);
        reporter.finishReport();
      }, " - open-root: r0\n" +
          "/ - add-dir\n" +
          "/ - add-file\n" +
          "/ - change-dir-prop: svn:entry:committed-date\n" +
          "/ - change-dir-prop: svn:entry:committed-rev\n" +
          "/ - change-dir-prop: svn:entry:last-author\n" +
          "/ - change-dir-prop: svn:entry:uuid\n" +
          "/ - open-dir: r0\n" +
          "a/b/c/d - apply-text-delta: null\n" +
          "a/b/c/d - change-file-prop: svn:entry:committed-date\n" +
          "a/b/c/d - change-file-prop: svn:entry:committed-rev\n" +
          "a/b/c/d - change-file-prop: svn:entry:last-author\n" +
          "a/b/c/d - change-file-prop: svn:entry:uuid\n" +
          "a/b/c/d - close-file: e08b5cff98d6e3f8a892fc999622d441\n" +
          "a/b/c/d - delta-chunk\n" +
          "a/b/c/d - delta-end\n" +
          "a/b/e - apply-text-delta: null\n" +
          "a/b/e - change-file-prop: svn:entry:committed-date\n" +
          "a/b/e - change-file-prop: svn:entry:committed-rev\n" +
          "a/b/e - change-file-prop: svn:entry:last-author\n" +
          "a/b/e - change-file-prop: svn:entry:uuid\n" +
          "a/b/e - close-file: babc2f91dac8ef35815e635d89196696\n" +
          "a/b/e - delta-chunk\n" +
          "a/b/e - delta-end\n");

      // svn update
      check(server, "a", SVNDepth.UNKNOWN, reporter -> {
        reporter.setPath("", null, revision, SVNDepth.INFINITY, false);
        reporter.finishReport();
      }, " - open-root: r0\n");
    }
  }

  @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider.class)
  public void files(@NotNull SvnTesterFactory factory) throws Exception {
    try (SvnTester server = create(factory)) {
      final long revision = server.openSvnRepository().getLatestRevision();
      // svn checkout --depth infinity a
      check(server, "a/b", SVNDepth.FILES, reporter -> {
        reporter.setPath("", null, revision, SVNDepth.INFINITY, true);
        reporter.finishReport();
      }, " - open-root: r0\n" +
          "/ - add-file\n" +
          "/ - change-dir-prop: svn:entry:committed-date\n" +
          "/ - change-dir-prop: svn:entry:committed-rev\n" +
          "/ - change-dir-prop: svn:entry:last-author\n" +
          "/ - change-dir-prop: svn:entry:uuid\n" +
          "/ - open-dir: r0\n" +
          "a/b/e - apply-text-delta: null\n" +
          "a/b/e - change-file-prop: svn:entry:committed-date\n" +
          "a/b/e - change-file-prop: svn:entry:committed-rev\n" +
          "a/b/e - change-file-prop: svn:entry:last-author\n" +
          "a/b/e - change-file-prop: svn:entry:uuid\n" +
          "a/b/e - close-file: babc2f91dac8ef35815e635d89196696\n" +
          "a/b/e - delta-chunk\n" +
          "a/b/e - delta-end\n");

      // svn update
      check(server, "a/b", SVNDepth.UNKNOWN, reporter -> {
        reporter.setPath("", null, revision, SVNDepth.FILES, false);
        reporter.finishReport();
      }, " - open-root: r0\n");

      // svn update --set-depth infinity
      check(server, "a/b", SVNDepth.INFINITY, reporter -> {
        reporter.setPath("", null, revision, SVNDepth.FILES, false);
        reporter.finishReport();
      }, " - open-root: r0\n" +
          "/ - add-dir\n" +
          "/ - add-file\n" +
          "/ - change-dir-prop: svn:entry:committed-date\n" +
          "/ - change-dir-prop: svn:entry:committed-rev\n" +
          "/ - change-dir-prop: svn:entry:last-author\n" +
          "/ - change-dir-prop: svn:entry:uuid\n" +
          "/ - open-dir: r0\n" +
          "a/b/c/d - apply-text-delta: null\n" +
          "a/b/c/d - change-file-prop: svn:entry:committed-date\n" +
          "a/b/c/d - change-file-prop: svn:entry:committed-rev\n" +
          "a/b/c/d - change-file-prop: svn:entry:last-author\n" +
          "a/b/c/d - change-file-prop: svn:entry:uuid\n" +
          "a/b/c/d - close-file: e08b5cff98d6e3f8a892fc999622d441\n" +
          "a/b/c/d - delta-chunk\n" +
          "a/b/c/d - delta-end\n");
    }
  }

  @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider.class)
  public void immediates(@NotNull SvnTesterFactory factory) throws Exception {
    try (SvnTester server = create(factory)) {
      final long revision = server.openSvnRepository().getLatestRevision();
      // svn checkout --depth immediates a/b
      check(server, "a/b", SVNDepth.IMMEDIATES, reporter -> {
        reporter.setPath("", null, revision, SVNDepth.INFINITY, true);
        reporter.finishReport();
      }, " - open-root: r0\n" +
          "/ - add-dir\n" +
          "/ - add-file\n" +
          "/ - change-dir-prop: svn:entry:committed-date\n" +
          "/ - change-dir-prop: svn:entry:committed-rev\n" +
          "/ - change-dir-prop: svn:entry:last-author\n" +
          "/ - change-dir-prop: svn:entry:uuid\n" +
          "/ - open-dir: r0\n" +
          "a/b/e - apply-text-delta: null\n" +
          "a/b/e - change-file-prop: svn:entry:committed-date\n" +
          "a/b/e - change-file-prop: svn:entry:committed-rev\n" +
          "a/b/e - change-file-prop: svn:entry:last-author\n" +
          "a/b/e - change-file-prop: svn:entry:uuid\n" +
          "a/b/e - close-file: babc2f91dac8ef35815e635d89196696\n" +
          "a/b/e - delta-chunk\n" +
          "a/b/e - delta-end\n");

      // svn update
      check(server, "a/b", SVNDepth.UNKNOWN, reporter -> {
        reporter.setPath("", null, revision, SVNDepth.IMMEDIATES, false);
        reporter.finishReport();
      }, " - open-root: r0\n");

      // svn update --set-depth infinity
      check(server, "a/b", SVNDepth.INFINITY, reporter -> {
        reporter.setPath("", null, revision, SVNDepth.IMMEDIATES, false);
        reporter.finishReport();
      }, " - open-root: r0\n" +
          "/ - add-file\n" +
          "/ - change-dir-prop: svn:entry:committed-date\n" +
          "/ - change-dir-prop: svn:entry:committed-rev\n" +
          "/ - change-dir-prop: svn:entry:last-author\n" +
          "/ - change-dir-prop: svn:entry:uuid\n" +
          "/ - open-dir: r0\n" +
          "a/b/c/d - apply-text-delta: null\n" +
          "a/b/c/d - change-file-prop: svn:entry:committed-date\n" +
          "a/b/c/d - change-file-prop: svn:entry:committed-rev\n" +
          "a/b/c/d - change-file-prop: svn:entry:last-author\n" +
          "a/b/c/d - change-file-prop: svn:entry:uuid\n" +
          "a/b/c/d - close-file: e08b5cff98d6e3f8a892fc999622d441\n" +
          "a/b/c/d - delta-chunk\n" +
          "a/b/c/d - delta-end\n");
    }
  }

  @Test(dataProvider = "all", dataProviderClass = SvnTesterDataProvider.class)
  public void complex(@NotNull SvnTesterFactory factory) throws Exception {
    try (SvnTester server = create(factory)) {
      checkout("", SVNDepth.INFINITY);
      Assert.assertTrue(new File(wc, "a/b/c/d").exists());

      update("a/b", SVNDepth.FILES);
      Assert.assertFalse(new File(wc, "a/b/c").exists());
      Assert.assertTrue(new File(wc, "a/b/e").exists());

      update("", SVNDepth.INFINITY);
      Assert.assertTrue(new File(wc, "a/b/c").exists());
      Assert.assertTrue(new File(wc, "a/b/c/d").exists());

      update("a/b", SVNDepth.EMPTY);
      Assert.assertFalse(new File(wc, "a/b/c").exists());
      Assert.assertFalse(new File(wc, "a/b/e").exists());

      update("", SVNDepth.INFINITY);
      Assert.assertTrue(new File(wc, "a/b/c/d").exists());
      Assert.assertTrue(new File(wc, "a/b/e").exists());

      update("a/b", SVNDepth.IMMEDIATES);
      Assert.assertTrue(new File(wc, "a/b/c").exists());
      Assert.assertTrue(new File(wc, "a/b/e").exists());
      Assert.assertFalse(new File(wc, "a/b/c/d").exists());

      update("", SVNDepth.INFINITY);
      Assert.assertTrue(new File(wc, "a/b/c/d").exists());
    }
  }

  private void check(@NotNull SvnTester server, @NotNull String path, @Nullable SVNDepth depth, @NotNull ISVNReporterBaton reporterBaton, @NotNull String expected) throws SVNException {
    final SVNRepository repo = server.openSvnRepository();
    final ReportSVNEditor editor = new ReportSVNEditor();
    repo.update(repo.getLatestRevision(), path, depth, false, reporterBaton, editor);
    Assert.assertEquals(editor.toString(), expected);
  }
}
