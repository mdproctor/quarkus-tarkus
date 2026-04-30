package io.casehub.work.queues.service;

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

@ApplicationScoped
public class LambdaFilterRegistry {

    @Inject
    Instance<WorkItemFilterBean> beans;

    public List<WorkItemFilterBean> all() {
        var list = new ArrayList<WorkItemFilterBean>();
        beans.forEach(list::add);
        return list;
    }
}
