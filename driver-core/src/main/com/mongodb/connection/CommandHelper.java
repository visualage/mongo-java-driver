/*
 * Copyright (c) 2008-2014 MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb.connection;

import com.mongodb.MongoNamespace;
import com.mongodb.MongoServerException;
import com.mongodb.async.SingleResultCallback;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.codecs.BsonDocumentCodec;

import static com.mongodb.MongoNamespace.COMMAND_COLLECTION_NAME;
import static com.mongodb.ReadPreference.primary;

final class CommandHelper {
    static BsonDocument executeCommand(final String database, final BsonDocument command, final InternalConnection internalConnection) {
        return sendAndReceive(database, command, internalConnection);
    }

    static BsonDocument executeCommandWithoutCheckingForFailure(final String database, final BsonDocument command,
                                                                final InternalConnection internalConnection) {
        try {
            return sendAndReceive(database, command, internalConnection);
        } catch (MongoServerException e) {
            return new BsonDocument();
        }
    }

    static void executeCommandAsync(final String database, final BsonDocument command, final InternalConnection internalConnection,
                                    final SingleResultCallback<BsonDocument> callback) {
        final SimpleCommandMessage message =
                new SimpleCommandMessage(new MongoNamespace(database, COMMAND_COLLECTION_NAME).getFullName(), command, primary(),
                                                MessageSettings.builder()
                                                        .serverVersion(internalConnection.getDescription().getServerVersion())
                                                        .build());

        internalConnection.sendAndReceiveAsync(message, new BsonDocumentCodec(), new SingleResultCallback<BsonDocument>() {
            @Override
            public void onResult(final BsonDocument result, final Throwable t) {
                if (t != null) {
                    callback.onResult(null, t);
                } else {
                    callback.onResult(result, null);
                }
            }
        });
    }

    static boolean isCommandOk(final BsonDocument response) {
        if (!response.containsKey("ok")) {
            return false;
        }
        BsonValue okValue = response.get("ok");
        if (okValue.isBoolean()) {
            return okValue.asBoolean().getValue();
        } else if (okValue.isNumber()) {
            return okValue.asNumber().intValue() == 1;
        } else {
            return false;
        }
    }

    private static BsonDocument sendAndReceive(final String database, final BsonDocument command,
                                               final InternalConnection internalConnection) {
        SimpleCommandMessage message = new SimpleCommandMessage(new MongoNamespace(database, COMMAND_COLLECTION_NAME).getFullName(),
                                                                       command, primary(),
                                                                       MessageSettings
                                                                               .builder()
                                                                               // Note: server version will be 0.0 at this point when called
                                                                               // from InternalConnectionInitializer, which means OP_MSG
                                                                               // will not be used
                                                                               .serverVersion(internalConnection.getDescription()
                                                                                                      .getServerVersion())
                                                                               .build());
        return internalConnection.sendAndReceive(message, new BsonDocumentCodec());
    }

    private CommandHelper() {
    }
}
