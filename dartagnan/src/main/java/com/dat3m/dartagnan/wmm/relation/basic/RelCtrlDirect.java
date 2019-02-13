package com.dat3m.dartagnan.wmm.relation.basic;

import com.dat3m.dartagnan.program.event.Event;
import com.dat3m.dartagnan.program.event.If;
import com.dat3m.dartagnan.program.utils.EType;
import com.dat3m.dartagnan.wmm.filter.FilterBasic;
import com.dat3m.dartagnan.wmm.utils.Tuple;
import com.dat3m.dartagnan.wmm.utils.TupleSet;

public class RelCtrlDirect extends BasicRelation {

    public RelCtrlDirect(){
        term = "ctrlDirect";
        isStatic = true;
    }

    @Override
    public TupleSet getMaxTupleSet(){
        if(maxTupleSet == null){
            maxTupleSet = new TupleSet();
            for(Event e1 : program.getCache().getEvents(FilterBasic.get(EType.CMP))){
                for(Event e2 : ((If) e1).getT1().getEvents()){
                    maxTupleSet.add(new Tuple(e1, e2));
                }
                for(Event e2 : ((If) e1).getT2().getEvents()){
                    maxTupleSet.add(new Tuple(e1, e2));
                }
            }
        }
        return maxTupleSet;
    }
}
