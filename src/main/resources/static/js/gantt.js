const ganttScroll = document.getElementById('gantt-scroll');
const ganttModes = document.getElementById('gantt-modes');

if (ganttScroll && ganttModes) {
    const modeButtons = ganttModes.querySelectorAll('button[data-gantt-mode]');
    const today = ganttScroll.querySelector('thead time[aria-current="date"]');
    const taskHeading = ganttScroll.querySelector('thead .gantt-task');

    ganttModes.hidden = false;
    modeButtons.forEach((button) => {
        button.addEventListener('click', () => {
            const mode = button.dataset.ganttMode;
            if (ganttScroll.dataset.mode === mode) {
                return;
            }

            ganttScroll.dataset.mode = mode;
            modeButtons.forEach((modeButton) => {
                modeButton.setAttribute('aria-pressed', String(modeButton === button));
            });
            ganttScroll.scrollLeft = 0;

            if (mode === 'detail' && today) {
                const todayBounds = today.getBoundingClientRect();
                const timelineLeft = taskHeading.getBoundingClientRect().right;
                const timelineRight = ganttScroll.getBoundingClientRect().left
                    + ganttScroll.clientLeft + ganttScroll.clientWidth;
                const timelineCenter = (timelineLeft + timelineRight) / 2;
                ganttScroll.scrollLeft = todayBounds.left + todayBounds.width / 2 - timelineCenter;
            }
        });
    });
}

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
