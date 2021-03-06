/*
 * This file is part of git-as-svn. It is subject to the license terms
 * in the LICENSE file found in the top-level directory of this distribution
 * and at http://www.gnu.org/licenses/gpl-2.0.html. No part of git-as-svn,
 * including this file, may be copied, modified, propagated, or distributed
 * except according to the terms contained in the LICENSE file.
 */
package svnserver.ext.gitlfs.storage.local;

import org.apache.commons.codec.binary.Hex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.bozaro.gitlfs.common.JsonHelper;
import ru.bozaro.gitlfs.pointer.Constants;
import ru.bozaro.gitlfs.pointer.Pointer;
import svnserver.HashHelper;
import svnserver.auth.User;
import svnserver.ext.gitlfs.config.LocalLfsConfig;
import svnserver.ext.gitlfs.storage.LfsWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

/**
 * Local storage writer.
 *
 * @author Artem V. Navrotskiy <bozaro@users.noreply.github.com>
 * @author Marat Radchenko <marat@slonopotamus.org>
 */
public final class LfsLocalWriter extends LfsWriter {
  private final LocalLfsConfig.LfsLayout layout;
  @NotNull
  private final Path dataRoot;
  @Nullable
  private final Path metaRoot;
  @NotNull
  private final Path dataTemp;
  @Nullable
  private final Path metaTemp;
  private final boolean compress;
  @Nullable
  private final User user;
  @NotNull
  private final MessageDigest digestMd5;
  @NotNull
  private final MessageDigest digestSha;
  @Nullable
  private OutputStream dataStream;
  private long size;

  LfsLocalWriter(@NotNull LocalLfsConfig.LfsLayout layout, @NotNull Path dataRoot, @Nullable Path metaRoot, boolean compress, @Nullable User user) throws IOException {
    this.layout = layout;
    this.dataRoot = dataRoot;
    this.metaRoot = metaRoot;
    this.compress = compress;
    this.user = user;

    final String prefix = UUID.randomUUID().toString();

    dataTemp = Files.createDirectories(dataRoot.resolve("tmp")).resolve(prefix + ".tmp");
    metaTemp = metaRoot == null ? null : Files.createDirectories(metaRoot.resolve("tmp")).resolve(prefix + ".tmp");

    digestMd5 = HashHelper.md5();
    digestSha = HashHelper.sha256();
    size = 0;
    if (this.compress) {
      dataStream = new GZIPOutputStream(Files.newOutputStream(dataTemp));
    } else {
      dataStream = Files.newOutputStream(dataTemp);
    }
  }

  @Override
  public void write(int b) throws IOException {
    if (dataStream == null) {
      throw new IllegalStateException();
    }
    dataStream.write(b);
    digestMd5.update((byte) b);
    digestSha.update((byte) b);
    size += 1;
  }

  @Override
  public void write(@NotNull byte[] b, int off, int len) throws IOException {
    if (dataStream == null) {
      throw new IllegalStateException();
    }
    dataStream.write(b, off, len);
    digestMd5.update(b, off, len);
    digestSha.update(b, off, len);
    size += len;
  }

  @Override
  public void close() throws IOException {
    try {
      if (dataStream != null) {
        dataStream.close();
        dataStream = null;
      }
    } finally {
      Files.deleteIfExists(dataTemp);
    }
  }

  @NotNull
  @Override
  public String finish(@Nullable String expectedOid) throws IOException {
    if (dataStream == null) {
      throw new IllegalStateException();
    }

    try {
      dataStream.close();
      dataStream = null;

      final byte[] sha = digestSha.digest();
      final byte[] md5 = digestMd5.digest();

      final String oid = LfsLocalStorage.OID_PREFIX + Hex.encodeHexString(sha);
      if (expectedOid != null && !expectedOid.equals(oid)) {
        throw new IOException("Invalid stream checksum: expected " + expectedOid + ", but actual " + oid);
      }

      // Write file data
      final Path dataPath = LfsLocalStorage.getPath(layout, dataRoot, oid, compress ? ".gz" : "");
      if (dataPath == null)
        throw new IllegalStateException();

      try {
        Files.createDirectories(dataPath.getParent());
        Files.move(dataTemp, dataPath);
      } catch (IOException e) {
        if (!Files.isRegularFile(dataPath))
          throw e;
      }

      // Write metadata
      if (metaRoot != null) {
        final Path metaPath = LfsLocalStorage.getPath(layout, metaRoot, oid, ".meta");
        if (metaPath == null)
          throw new IllegalStateException();

        Files.createDirectories(metaPath.getParent());

        if (!Files.exists(metaPath) && metaTemp != null) {
          try (OutputStream stream = Files.newOutputStream(metaTemp)) {
            final Map<String, String> map = new HashMap<>();
            map.put(Constants.SIZE, String.valueOf(size));
            map.put(Constants.OID, oid);
            map.put(LfsLocalStorage.HASH_MD5, Hex.encodeHexString(md5));
            map.put(LfsLocalStorage.CREATE_TIME, JsonHelper.dateFormat.format(new Date()));
            if ((user != null) && (!user.isAnonymous())) {
              if (user.getEmail() != null) {
                map.put(LfsLocalStorage.META_EMAIL, user.getEmail());
              }
              map.put(LfsLocalStorage.META_USER_NAME, user.getUsername());
              map.put(LfsLocalStorage.META_REAL_NAME, user.getRealName());
            }
            stream.write(Pointer.serializePointer(map));
            stream.close();

            try {
              Files.move(metaTemp, metaPath);
            } catch (IOException e) {
              if (!Files.isRegularFile(metaPath))
                throw e;
            }
          } finally {
            Files.deleteIfExists(metaTemp);
          }
        }
      }
      return oid;
    } finally {
      Files.deleteIfExists(dataTemp);
    }
  }
}
