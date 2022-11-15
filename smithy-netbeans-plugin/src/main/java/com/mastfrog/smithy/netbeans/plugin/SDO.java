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
