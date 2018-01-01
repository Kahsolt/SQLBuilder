package tk.kahsolt.sqlbuilder.sql;

import java.util.ArrayList;

public class Transaction {

    private ArrayList<String> blocks;

    public Transaction() { }

    public Transaction block(String block) {
        if(blocks==null) blocks = new ArrayList<>();
        String blk = block.trim();
        if(blk.endsWith(";"))
            blocks.add(block.substring(0, blk.length()-1));
        else
            blocks.add(block);
        return this;
    }

    public String commit() {
        String blks = String.join("; ", blocks);
        return String.format("BEGIN; %s; COMMIT;", blks);
    }

}
