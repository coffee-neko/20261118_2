const taskRows = document.getElementById('gantt-tasks');

if (taskRows) {
    const childrenByParent = new Map();

    taskRows.querySelectorAll('tr[data-parent-id]').forEach((row) => {
        const parentId = row.dataset.parentId;
        if (!childrenByParent.has(parentId)) {
            childrenByParent.set(parentId, []);
        }
        childrenByParent.get(parentId).push(row);
    });

    taskRows.querySelectorAll('button[data-toggle-children]').forEach((button) => {
        const children = childrenByParent.get(button.dataset.toggleChildren);
        if (!children) {
            return;
        }

        button.hidden = false;
        button.addEventListener('click', () => {
            const wasExpanded = button.getAttribute('aria-expanded') === 'true';
            children.forEach((row) => { row.hidden = wasExpanded; });
            button.setAttribute('aria-expanded', String(!wasExpanded));
            button.setAttribute('aria-label', wasExpanded ? '子タスクを開く' : '子タスクを閉じる');
            button.textContent = wasExpanded ? '開く' : '閉じる';
        });
    });
}
