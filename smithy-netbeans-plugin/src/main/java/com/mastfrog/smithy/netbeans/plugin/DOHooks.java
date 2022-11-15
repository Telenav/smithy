/*
 * The MIT License
 *
 * Copyright 2022 Mastfrog Technologies.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
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
