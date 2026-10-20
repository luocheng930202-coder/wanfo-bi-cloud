/** Bounded document history. Saving does not erase editing history or change the saved baseline. */
export class DocumentHistory {
  private entries:string[]=[]
  private index=-1
  constructor(private capacity=50){}
  reset(value:string){this.entries=[value];this.index=0}
  commit(value:string){if(this.entries[this.index]===value)return;this.entries=this.entries.slice(0,this.index+1);this.entries.push(value);if(this.entries.length>this.capacity)this.entries.shift();this.index=this.entries.length-1}
  get canUndo(){return this.index>0}
  get canRedo(){return this.index<this.entries.length-1}
  undo(){return this.canUndo?this.entries[--this.index]:null}
  redo(){return this.canRedo?this.entries[++this.index]:null}
}
