/* 
 * Copyright 2023 Telenav.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mastfrog.smithy.netbeans.plugin;

import java.io.IOException;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataFolder;
import org.openide.loaders.DataLoader;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectExistsException;
import org.openide.util.HelpCtx;

/**
 *
 * @author Tim Boudreau
 */
public class SDO extends DataObject {

    public SDO(FileObject pf, DataLoader loader) throws DataObjectExistsException {
        super(pf, loader);
    }
    
    @Override
    public boolean isDeleteAllowed() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean isCopyAllowed() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean isMoveAllowed() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean isRenameAllowed() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public HelpCtx getHelpCtx() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    protected DataObject handleCopy(DataFolder df) throws IOException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    protected void handleDelete() throws IOException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    protected FileObject handleRename(String string) throws IOException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    protected FileObject handleMove(DataFolder df) throws IOException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    protected DataObject handleCreateFromTemplate(DataFolder df, String string) throws IOException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

}
