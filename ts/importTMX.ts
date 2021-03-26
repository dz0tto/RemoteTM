/*******************************************************************************
Copyright (c) 2008-2021 - Maxprograms,  http://www.maxprograms.com/

Permission is hereby granted, free of charge, to any person obtaining a copy of 
this software and associated documentation files (the "Software"), to compile, 
modify and use the Software in its executable form without restrictions.

Redistribution of this Software or parts of it in any form (source code or 
executable binaries) requires prior written permission from Maxprograms.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR 
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, 
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE 
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER 
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, 
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE 
SOFTWARE.
*******************************************************************************/

import { Dashboard } from "./dashboard";
import { Dialog } from "./dialog";
import { DropZone } from "./dropZone";
import { Message } from "./message";
import { RemoteTM } from "./remotetm";

export class ImportTMX {

    parent: Dashboard;
    dialog: Dialog;
    dropZone: DropZone

    constructor(parent: Dashboard) {
        this.parent = parent;

        this.dialog = new Dialog(400);
        this.dialog.setTitle('Import TMX');

        this.dropZone = new DropZone(this.dialog.contentArea);

        let importButton: HTMLButtonElement = document.createElement('button');
        importButton.innerText = 'Import TMX';
        importButton.addEventListener('click', () => { this.importTMX(); });
        this.dialog.addButton(importButton);
    }

    open(): void {
        this.dialog.open();
    }

    importTMX(): void {
        let files: FileList = this.dropZone.getFiles();
        if (files.length === 0) {
            new Message('Select file');
            return;
        }
        let formData = new FormData();
        formData.append("file", files[0]);
        fetch(RemoteTM.getMainURL() + '/upload', {
            method: 'POST',
            headers: [
                ['Session', RemoteTM.getSession()],
                ['Content-Type', 'multipart/form-data'],
                ['Accept', 'application/json']
            ],
            body: formData
        }).then(async (response: Response) => {
            let json: any = await response.json();
            console.log(JSON.stringify(json));
            if (json.status === 'OK') {

                this.dialog.close();
            } else {
                new Message(json.reason);
            }
        }).catch((reason: any) => {
            console.error('Error:', reason);
        });
    }
}