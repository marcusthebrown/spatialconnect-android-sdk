/**
 * Copyright 2018-present Boundless, http://boundlessgeo.com
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License
 */
package com.boundlessgeo.spatialconnect.jsbridge;

/**
 * A list of actions that don't belong in the schema project because they are only relevant
 * to the mobile SDK domain.  If more than one service will send the action, then move it to the
 * schema repo so it can be accessed from {@link com.boundlessgeo.schema.Actions}.
 */
public enum SCActions {
  DELETE_SC_DATASTORE("DELETE_SC_DATASTORE"),
  DELETE_ALL_SC_DATASTORES("DELETE_ALL_SC_DATASTORES"),
  ;

  private final String action;

  SCActions(String action) {
    this.action = action;
  }

  public String value() {
    return this.action;
  }

  public static SCActions fromAction(String action) {
    SCActions[] var1 = values();
    int var2 = var1.length;

    for(int var3 = 0; var3 < var2; ++var3) {
      SCActions v = var1[var3];
      if(v.action.equalsIgnoreCase(action)) {
        return v;
      }
    }

    throw new IllegalArgumentException(action + " is not a valid SCAction.");
  }

}
