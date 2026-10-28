(function (global) {
    'use strict';
    global.createHandyRefreshController = function (options) {
        var later = options.setTimeout || setTimeout;
        var cancel = options.clearTimeout || clearTimeout;
        var now = options.now || Date.now;
        var queued = null, watch = null, pending = false, started = 0, retries = 0;
        var retryAt = 8000, generation = 0;
        function queue() {
            // Throttle, rather than continually restarting a debounce timer.
            if (queued !== null) return;
            queued = later(function () { queued = null; options.project(); }, 100);
        }
        function tick(token) {
            if (!pending || token !== generation) return;
            options.project();
            if (!pending || token !== generation) return;
            var elapsed = now() - started;
            if (elapsed >= 35000) {
                pending = false;
                options.busy(false);
                options.timeout();
                return;
            }
            // Retry only a settled, unready background source. Never interrupt
            // a document that is still navigating or a visible login form.
            if (elapsed >= retryAt && retries < 2 && options.settled()) {
                retries++;
                retryAt = elapsed + 12000;
                options.reload();
            }
            watch = later(function () { tick(token); }, 300);
        }
        function begin(reload) {
            if (pending) { queue(); return false; }
            pending = true;
            started = now(); retries = 0; retryAt = 8000;
            var token = ++generation;
            options.busy(true);
            if (reload) options.reload();
            watch = later(function () { tick(token); }, 100);
            queue();
            return true;
        }
        function finish() {
            if (!pending) return;
            pending = false; generation++;
            if (watch !== null) cancel(watch);
            watch = null;
            options.busy(false);
        }
        return { queue: queue, start: function () { return begin(false); },
            refresh: function () { return begin(true); }, finish: finish,
            isPending: function () { return pending; },
            dispose: function () {
                pending = false; generation++;
                if (queued !== null) cancel(queued);
                if (watch !== null) cancel(watch);
                queued = watch = null;
            }
        };
    };
})(globalThis);
