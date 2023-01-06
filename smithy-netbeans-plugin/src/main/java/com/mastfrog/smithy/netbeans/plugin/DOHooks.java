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

import com.mastfrog.function.throwing.io.IOBiFunction;
import com.mastfrog.function.throwing.io.IOFunction;
import com.mastfrog.function.throwing.io.IORunnable;
import com.mastfrog.function.throwing.io.IOTriFunction;
import java.io.IOException;
import java.util.function.Supplier;
import org.nemesis.antlr.spi.language.DataObjectHooks;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataFolder;
import org.openide.loaders.DataObject;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.openide.util.lookup.InstanceContent;

/**
 *
 * @author Tim Boudreau
 */
public class DOHooks implements DataObjectHooks {

    @Override
    public void notifyCreated(DataObject dob) {
        DataObjectHooks.super.notifyCreated(dob);
    }

    @Override
    public void decorateLookup(DataObject on, InstanceContent content, Supplier<Lookup> superGetLookup) {
        DataObjectHooks.super.decorateLookup(on, content, superGetLookup);
    }

    @Override
    public Node createNodeDelegate(DataObject on, Supplier<Node> superDelegate) {
        return DataObjectHooks.super.createNodeDelegate(on, superDelegate);
    }

    @Override
    public void handleDelete(DataObject obj, IORunnable superHandleDelete) throws IOException {
        DataObjectHooks.super.handleDelete(obj, superHandleDelete);
    }

    @Override
    public FileObject handleRename(DataObject on, String name, IOFunction<String, FileObject> superHandleRename) throws IOException {
        return DataObjectHooks.super.handleRename(on, name, superHandleRename);
    }

    @Override
    public DataObject handleCopy(DataObject on, DataFolder target, IOFunction<DataFolder, DataObject> superHandleCopy) throws IOException {
        return DataObjectHooks.super.handleCopy(on, target, superHandleCopy);
    }

    @Override
    public FileObject handleMove(DataObject on, DataFolder target, IOFunction<DataFolder, FileObject> superHandleMove) throws IOException {
        return DataObjectHooks.super.handleMove(on, target, superHandleMove);
    }

    @Override
    public DataObject handleCreateFromTemplate(DataObject on, DataFolder df, String name, IOBiFunction<DataFolder, String, DataObject> superHandleCreateFromTemplate) throws IOException {
        return DataObjectHooks.super.handleCreateFromTemplate(on, df, name, superHandleCreateFromTemplate);
    }

    @Override
    public DataObject handleCopyRename(DataObject on, DataFolder df, String name, String ext, IOTriFunction<DataFolder, String, String, DataObject> superHandleRenameCopy) throws IOException {
        return DataObjectHooks.super.handleCopyRename(on, df, name, ext, superHandleRenameCopy);
    }

    
}
