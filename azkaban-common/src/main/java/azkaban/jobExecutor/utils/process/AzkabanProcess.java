/*
 * Copyright 2012 LinkedIn Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package azkaban.jobExecutor.utils.process;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import azkaban.utils.Utils;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import azkaban.utils.LogGobbler;

import com.google.common.base.Joiner;

/**
 * An improved version of java.lang.Process.
 * 
 * Output is read by separate threads to avoid deadlock and logged to log4j
 * loggers.
 */
public class AzkabanProcess {
  
  public static String KILL_COMMAND = "kill";
  
  private final String workingDir;
  private volatile List<String> cmd;
  private final Map<String, String> env;
  private final Logger logger;
  private final CountDownLatch startupLatch;
  private final CountDownLatch completeLatch;

  private volatile int processId;
  private volatile Process process;

  private boolean isExecuteAsUser = false;
  private String executeAsUserBinary = null;
  private String effectiveUser = null;

  private final String hostMex = "^((\\d|\\w)+@)?(\\d{1,3}.){3}\\d{1,3}$";
  private final List<String> sEnv = new ArrayList<String>(Arrays.asList("sh","/bin/bash","python","ruby","java","hadoop"));

  private volatile String host = null;
  private volatile Set<Integer> pids;
  private volatile String exe = null;
  private volatile String aHost;
  private volatile Map<Integer,Integer> pd;
  private volatile int gid;

  public AzkabanProcess(final List<String> cmd, final Map<String, String> env,
      final String workingDir, final Logger logger) {
    this.cmd = cmd;
    this.env = env;
    this.workingDir = workingDir;
    this.processId = -1;
    this.startupLatch = new CountDownLatch(1);
    this.completeLatch = new CountDownLatch(1);
    this.logger = logger;
  }

  public AzkabanProcess(List<String> cmd, Map<String, String> env,
      String workingDir, Logger logger, String executeAsUserBinary,
      String effectiveUser) {
    this(cmd, env, workingDir, logger);
    this.isExecuteAsUser = true;
    this.executeAsUserBinary = executeAsUserBinary;
    this.effectiveUser = effectiveUser;
  }

  /**
   * Execute this process, blocking until it has completed.
   */
  public void run() throws IOException {
    if (this.isStarted() || this.isComplete()) {
      throw new IllegalStateException("The process can only be used once.");
    }

    ProcessBuilder builder = new ProcessBuilder(cmd);
    builder.directory(new File(workingDir));
    builder.environment().putAll(env);
    builder.redirectErrorStream(true);
    this.process = builder.start();
    try {
      this.processId = processId(process);
      if (processId == 0) {
        logger.debug("Spawned thread with unknown process id");
      } else {
        logger.debug("Spawned thread with process id " + processId);
      }

      this.startupLatch.countDown();

      LogGobbler outputGobbler =
          new LogGobbler(new InputStreamReader(process.getInputStream()),
              logger, Level.INFO, 30);
      LogGobbler errorGobbler =
          new LogGobbler(new InputStreamReader(process.getErrorStream()),
              logger, Level.ERROR, 30);

      outputGobbler.start();
      errorGobbler.start();
      int exitCode = -1;
      try {
        exitCode = process.waitFor();
      } catch (InterruptedException e) {
        logger.info("Process interrupted. Exit code is " + exitCode, e);
      }

      completeLatch.countDown();

      // try to wait for everything to get logged out before exiting
      outputGobbler.awaitCompletion(5000);
      errorGobbler.awaitCompletion(5000);

      if (exitCode != 0) {
        String output =
            new StringBuilder().append("Stdout:\n")
                .append(outputGobbler.getRecentLog()).append("\n\n")
                .append("Stderr:\n").append(errorGobbler.getRecentLog())
                .append("\n").toString();
        throw new ProcessFailureException(exitCode, output);
      }

    } finally {
      IOUtils.closeQuietly(process.getInputStream());
      IOUtils.closeQuietly(process.getOutputStream());
      IOUtils.closeQuietly(process.getErrorStream());
    }
  }

  public AzkabanProcess initCmd(final String aHost){
    this.aHost = aHost;
    for (String cm : cmd) {
      Matcher pm = Pattern.compile(hostMex).matcher(cm);
      if (pm.find() && host == null) {
        host = pm.group();
        logger.info("ssh host : \t" + host);
        //break;
      } else {
        if (!sEnv.contains(cm.toLowerCase()) && host != null && exe == null && (cm.contains("/") || cm.contains("."))) {
          StringTokenizer scm = new StringTokenizer(cm);
          while (scm.hasMoreTokens() && exe == null) {
            String c = scm.nextToken();
            if (!sEnv.contains(c.toLowerCase()) && exe == null && (c.contains("/") || c.contains("."))) {
              exe = c;
            }
          }
          logger.info("execute script : " + exe);
        }
      }
    }

    String localHost = Utils.getHostIP();

    //剔除ssh本机
    if (host != null) {
      if (host.contains(localHost)) {
        this.logger.warn("ssh localhost : " + host);
        List<String> cmds = new ArrayList<String>();
        for (String c : cmd) {
          if (!"ssh".equals(c.toLowerCase()) && !host.equals(c)) {
            //cmds.add(c);
            cmds.add(c.replaceAll("\"",""));
          }
        }
        host = null;
        cmd = cmds;
        logger.info("changed script : " + cmd);
      }
    } else if (aHost != null && !aHost.contains(localHost)){
      //设置代码执行ssh主机
      List<String> cmds = new ArrayList<String>(Arrays.asList("ssh", aHost));

      for (String c : cmd) {
        cmds.add(c);
        if (!sEnv.contains(c.toLowerCase()) && exe == null && (c.contains("/") || c.contains("."))) {
          StringTokenizer scm = new StringTokenizer(c);
          while (scm.hasMoreTokens() && exe == null) {
            String cc = scm.nextToken();
            if (!sEnv.contains(cc.toLowerCase()) && exe == null && (cc.contains("/") || cc.contains("."))) {
              exe = cc;
            }
          }
          logger.info("execute script : " + exe);
        }
      }
      host = aHost;
      cmd = cmds;
      logger.info("ssh host ->" + "\t" + aHost);
    } else {
      logger.info("exe host ->" + "\t" + localHost);
    }
    return this;
  }

  /**
   * Await the completion of this process
   * 
   * @throws InterruptedException if the thread is interrupted while waiting.
   */
  public void awaitCompletion() throws InterruptedException {
    this.completeLatch.await();
  }

  /**
   * Await the start of this process
   * 
   * @throws InterruptedException if the thread is interrupted while waiting.
   */
  public void awaitStartup() throws InterruptedException {
    this.startupLatch.await();
  }

  /**
   * Get the process id for this process, if it has started.
   * 
   * @return The process id or -1 if it cannot be fetched
   */
  public int getProcessId() {
    checkStarted();
    return this.processId;
  }

  /**
   * Attempt to kill the process, waiting up to the given time for it to die
   * 
   * @param time The amount of time to wait
   * @param unit The time unit
   * @return true iff this soft kill kills the process in the given wait time.
   */
  public boolean softKill(final long time, final TimeUnit unit)
      throws InterruptedException {
    checkStarted();
    if (processId != 0 && isStarted()) {
      try {
        if (isExecuteAsUser) {
          String cmd =
              String.format("%s %s %s %d", executeAsUserBinary,
                  effectiveUser, KILL_COMMAND, processId);
          Runtime.getRuntime().exec(cmd);
        } else {
          String cmd = String.format("%s %d", KILL_COMMAND, processId);
          Runtime.getRuntime().exec(cmd);
        }
        return completeLatch.await(time, unit);
      } catch (IOException e) {
        logger.error("Kill attempt failed.", e);
      }
      return false;
    }
    return false;
  }

  /**
   * Force kill this process
   */
  public void hardKill() {
    checkStarted();
    if (isRunning()) {
      if (processId != 0) {
        try {
          if (isExecuteAsUser) {
            String cmd =
                String.format("%s %s %s -9 %d", executeAsUserBinary,
                    effectiveUser, KILL_COMMAND, processId);
            Runtime.getRuntime().exec(cmd);
          } else {
            String cmd = String.format("%s -9 %d", KILL_COMMAND, processId);
            Runtime.getRuntime().exec(cmd);
          }
        } catch (IOException e) {
          logger.error("Kill attempt failed.", e);
        }
      }
      process.destroy();
    }
  }

  /**
   * Attempt to get the process id for this process
   * 
   * @param process The process to get the id from
   * @return The id of the process
   */
  private int processId(final java.lang.Process process) {
    int processId = 0;
    try {
      Field f = process.getClass().getDeclaredField("pid");
      f.setAccessible(true);

      processId = f.getInt(process);
    } catch (Throwable e) {
      e.printStackTrace();
    }

    return processId;
  }

  /**
   * @return true iff the process has been started
   */
  public boolean isStarted() {
    return startupLatch.getCount() == 0L;
  }

  /**
   * @return true iff the process has completed
   */
  public boolean isComplete() {
    return completeLatch.getCount() == 0L;
  }

  /**
   * @return true iff the process is currently running
   */
  public boolean isRunning() {
    return isStarted() && !isComplete();
  }

  public void checkStarted() {
    if (!isStarted()) {
      throw new IllegalStateException("Process has not yet started.");
    }
  }

  @Override
  public String toString() {
    return "Process(cmd = " + Joiner.on(" ").join(cmd) + ", env = " + env
        + ", cwd = " + workingDir + ")";
  }

  public boolean isExecuteAsUser() {
    return isExecuteAsUser;
  }

  public String getEffectiveUser() {
    return effectiveUser;
  }
}
