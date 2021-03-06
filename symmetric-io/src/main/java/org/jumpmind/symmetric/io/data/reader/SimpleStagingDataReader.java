/**
 * Licensed to JumpMind Inc under one or more contributor
 * license agreements.  See the NOTICE file distributed
 * with this work for additional information regarding
 * copyright ownership.  JumpMind Inc licenses this file
 * to you under the GNU General Public License, version 3.0 (GPLv3)
 * (the "License"); you may not use this file except in compliance
 * with the License.
 *
 * You should have received a copy of the GNU General Public License,
 * version 3.0 (GPLv3) along with this library; if not, see
 * <http://www.gnu.org/licenses/>.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jumpmind.symmetric.io.data.reader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.math.BigDecimal;

import org.jumpmind.exception.IoException;
import org.jumpmind.symmetric.io.data.Batch.BatchType;
import org.jumpmind.symmetric.io.data.CsvConstants;
import org.jumpmind.symmetric.io.data.DataContext;
import org.jumpmind.symmetric.io.stage.IStagedResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleStagingDataReader {
    final static int MAX_WRITE_LENGTH = 32768;
    
    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected BatchType batchType;
    protected long batchId;
    protected String targetNodeId;
    protected boolean isRetry;
    protected IStagedResource stagedResource;
    protected BufferedWriter writer;
    protected DataContext context;
    protected BigDecimal maxKBytesPerSec;

    public SimpleStagingDataReader(BatchType batchType, long batchId, String targetNodeId, boolean isRetry,
            IStagedResource stagedResource, BufferedWriter writer, DataContext context, BigDecimal maxKBytesPerSec) {
        this.batchType = batchType;
        this.batchId = batchId;
        this.targetNodeId = targetNodeId;
        this.isRetry = isRetry;
        this.stagedResource = stagedResource;
        this.writer = writer;
        this.context = context;
        this.maxKBytesPerSec = maxKBytesPerSec;
    }

    public void process() {
        BufferedReader reader = stagedResource.getReader();
        try {
            // Retry means we've sent this batch before, so let's ask to retry the batch from the target's staging
            if (isRetry) {
                String line = null;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith(CsvConstants.BATCH)) {
                        writer.write(CsvConstants.RETRY + "," + batchId);
                        writer.newLine();
                        writer.write(CsvConstants.COMMIT + "," + batchId);
                        writer.newLine();
                        break;
                    } else {
                        writer.write(line);
                        writer.newLine();
                    }
                }
            } else {
                long totalCharsRead = 0, totalBytesRead = 0;
                int numCharsRead = 0, numBytesRead = 0;
                long startTime = System.currentTimeMillis(), ts = startTime, bts = startTime;
                boolean isThrottled = maxKBytesPerSec != null && maxKBytesPerSec.compareTo(BigDecimal.ZERO) > 0;
                long totalThrottleTime = 0;
                int bufferSize = MAX_WRITE_LENGTH;

                if (isThrottled) {
                    bufferSize = maxKBytesPerSec.multiply(new BigDecimal(1024)).intValue();
                }
                char[] buffer = new char[bufferSize];
        
                while ((numCharsRead = reader.read(buffer)) != -1) {
                    writer.write(buffer, 0, numCharsRead);
                    totalCharsRead += numCharsRead;
                    
                    if (Thread.currentThread().isInterrupted()) {
                        throw new IoException("This thread was interrupted");
                    }

                    if (System.currentTimeMillis() - ts > 60000) {
                        log.info("Batch '{}', for node '{}', for process 'send from stage' has been processing for {} seconds.  " +
                                "The following stats have been gathered: {}",
                                new Object[] { batchId, targetNodeId, (System.currentTimeMillis() - startTime) / 1000,
                                "CHARS=" + totalCharsRead });
                        ts = System.currentTimeMillis();
                    }

                    if (isThrottled) {
                        numBytesRead += new String(buffer, 0, numCharsRead).getBytes().length;
                        totalBytesRead += numBytesRead;
                        if (numBytesRead >= bufferSize) {                            
                            long expectedMillis = (long) (((numBytesRead / 1024f) / maxKBytesPerSec.floatValue()) * 1000);
                            long actualMillis = System.currentTimeMillis() - bts;
                            if (actualMillis <  expectedMillis) {
                                totalThrottleTime += expectedMillis - actualMillis;
                                Thread.sleep(expectedMillis - actualMillis);
                            }
                            numBytesRead = 0;
                            bts = System.currentTimeMillis();
                        }
                    }
                }
                if (log.isDebugEnabled() && totalThrottleTime > 0) {
                    log.debug("Batch '{}' for node '{}' took {}ms for {} bytes and was throttled for {}ms because limit is set to {} KB/s", 
                            batchId, targetNodeId, (System.currentTimeMillis() - startTime), totalBytesRead, totalThrottleTime,
                            maxKBytesPerSec);
                }
            }
        } catch (Throwable t) {
            throw new RuntimeException(t);
        } finally {
            stagedResource.close();
        }
    }
    
}
