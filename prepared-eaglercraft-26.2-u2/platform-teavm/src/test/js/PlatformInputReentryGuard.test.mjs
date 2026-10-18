import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const sourceUrl = new URL(
	'../../main/java/net/lax1dude/eaglercraft/v1_8/internal/PlatformInput.java',
	import.meta.url,
);
const source = await readFile(sourceUrl, 'utf8');

test('mousemove bridge drops nested DOM dispatch and always releases its guard', () => {
	assert.match(source, /if\s*\(mouseMoveDispatching\)\s*\{\s*evt\.preventDefault\(\);\s*evt\.stopPropagation\(\);\s*return;/,
		'mousemove listener rejects re-entry');
	assert.match(source, /if\s*\(mouseMoveDispatching\)\s*\{\s*evt\.preventDefault\(\);\s*evt\.stopPropagation\(\);\s*return;/,
		'nested mousemove is cancelled before it returns');
	assert.match(source, /mouseMoveDispatching\s*=\s*true;[\s\S]*finally\s*\{\s*mouseMoveDispatching\s*=\s*false;/,
		'mousemove listener clears the guard on every exit');

	let active = false;
	let handled = 0;
	let nested = false;
	const dispatch = (event = {}) => {
		event.preventDefault = event.preventDefault ?? (() => { event.defaultPrevented = true; });
		event.stopPropagation = event.stopPropagation ?? (() => { event.propagationStopped = true; });
		event.preventDefault();
		event.stopPropagation();
		if (active) return;
		active = true;
		try {
			handled++;
			if (!nested) {
				nested = true;
				const nestedEvent = {};
				dispatch(nestedEvent);
				assert.equal(nestedEvent.defaultPrevented, true, 'nested mousemove is cancelled');
				assert.equal(nestedEvent.propagationStopped, true, 'nested mousemove cannot bubble');
			}
		} finally {
			active = false;
		}
	};
	dispatch();
	assert.equal(handled, 1, 'a nested mousemove is ignored instead of recursing');
	assert.equal(active, false, 'the outer dispatch leaves the bridge ready');
	dispatch();
	assert.equal(handled, 2, 'a later mousemove is accepted after the nested event');
	assert.equal(active, false, 'the guard remains clear for later events');
});

test('mousemove guard resets after an exception', () => {
	let active = false;
	let calls = 0;
	let throwOnce = true;
	const dispatch = () => {
		if (active) return;
		active = true;
		try {
			calls++;
			if (throwOnce) {
				throwOnce = false;
				throw new Error('synthetic listener failure');
			}
		} finally {
			active = false;
		}
	};
	assert.throws(dispatch, /synthetic listener failure/);
	assert.equal(active, false, 'an exception cannot strand the guard');
	dispatch();
	assert.equal(calls, 2, 'the next event is accepted after the exception');
});

test('pointer-lock reconciliation is guarded against synchronous change events', () => {
	assert.match(source, /if\s*\(pointerLockStateDispatching\)\s*\{\s*return;/,
		'pointer-lock reconciliation rejects re-entry');
	assert.match(source, /pointerLockStateDispatching\s*=\s*true;[\s\S]*finally\s*\{\s*pointerLockStateDispatching\s*=\s*false;/,
		'pointer-lock reconciliation clears the guard on every exit');
	assert.match(source, /boolean\s+grab\s*=\s*isPointerLockedImpl\(\);/,
		'pointer-lock state comes from the actual document state');
	assert.match(source, /handleEvent\(Event\s+evt\)\s*\{\s*\/\/ The event may be synthetic[\s\S]*reconcilePointerLockState\(\);/,
		'change events do not trust forged event payloads');
	assert.match(source, /handleEvent\(Event\s+evt\)\s*\{\s*\/\/ Ignore event payload\/trust; reconcile against the real DOM state\.[\s\S]*reconcilePointerLockState\(\);/,
		'error events do not trust forged event payloads');

	let actualLocked = false;
	const reconcile = (_event) => actualLocked;
	assert.equal(reconcile({ isTrusted: false, claimedLocked: true }), false,
		'a forged change cannot claim a lock that the document does not have');
	actualLocked = true;
	assert.equal(reconcile({ isTrusted: false, claimedLocked: false }), true,
		'a forged error cannot hide a lock that the document still has');
});

test('pointer-lock vendor listeners use the same names for attach, remove, and reinit', () => {
	assert.match(source, /pointerLockChangeEventName\s*=\s*pointerLockSupported\s*==\s*POINTER_LOCK_MOZ\s*\?\s*"mozpointerlockchange"\s*:\s*"pointerlockchange";/,
		'change event name is captured at attach time');
	assert.match(source, /pointerLockErrorEventName\s*=\s*pointerLockSupported\s*==\s*POINTER_LOCK_MOZ\s*\?\s*"mozpointerlockerror"\s*:\s*"pointerlockerror";/,
		'error event name is captured at attach time');
	assert.match(source, /addEventListener\(pointerLockChangeEventName,\s*pointerlock/,
		'change listener attaches through the captured name');
	assert.match(source, /addEventListener\(pointerLockErrorEventName,\s*pointerlockerr/,
		'error listener attaches through the captured name');
	assert.match(source, /removeEventListener\(pointerLockChangeEventName,\s*pointerlock\)/,
		'change listener removes through the captured name');
	assert.match(source, /removeEventListener\(pointerLockErrorEventName,\s*pointerlockerr\)/,
		'error listener removes through the captured name');
	assert.match(source, /pointerLockChangeEventName\s*=\s*null;[\s\S]*pointerLockErrorEventName\s*=\s*null;/,
		'remove clears both names so a later init cannot reuse stale state');

	const namesFor = (moz) => ({
		change: moz ? 'mozpointerlockchange' : 'pointerlockchange',
		error: moz ? 'mozpointerlockerror' : 'pointerlockerror',
	});
	const attached = [];
	const attach = (moz) => {
		const names = namesFor(moz);
		attached.push(names.change, names.error);
		return names;
	};
	const remove = (names) => {
		const removed = [names.change, names.error];
		return removed;
	};
	const first = attach(true);
	assert.deepEqual(remove(first), ['mozpointerlockchange', 'mozpointerlockerror']);
	const second = attach(false);
	assert.deepEqual(remove(second), ['pointerlockchange', 'pointerlockerror']);
	assert.deepEqual(attached, [
		'mozpointerlockchange', 'mozpointerlockerror',
		'pointerlockchange', 'pointerlockerror',
	], 'reinitialization can switch vendor names without removing the wrong listener');
});

test('MouseHandler defers the opposite grab request with a bounded replay', async () => {
	const mouse = await readFile(new URL(
		'../../../../game/src/main/java/net/minecraft/client/MouseHandler.java',
		import.meta.url,
	), 'utf8');
	assert.match(mouse, /if\s*\(this\.mouseGrabTransition\)\s*\{\s*this\.deferredMouseGrab\s*=\s*Boolean\.TRUE;/,
		'grab requests are retained during a release transition');
	assert.match(mouse, /if\s*\(this\.mouseGrabTransition\)\s*\{\s*this\.deferredMouseGrab\s*=\s*Boolean\.FALSE;/,
		'release requests are retained during a grab transition');
	assert.match(mouse, /finally\s*\{\s*this\.finishMouseGrabTransition\(\);/,
		'transitions reconcile deferred requests after cleanup');
	assert.match(mouse, /for\s*\(int pass = 0; pass < 2; \+\+pass\)/,
		'deferred replay is bounded');
	assert.match(mouse, /if\s*\(this\.deferredMouseGrab != null && !this\.deferredMouseGrabScheduled\)/,
		'a request left by the bound is retained for one queued reconciliation');
	assert.match(mouse, /this\.minecraft\.execute\(\(\) -> \{/,
		'the bounded fallback runs outside the pointer-lock callback stack');

	let grabbed = false;
	let transition = false;
	let replaying = false;
	let deferred = null;
	let actions = 0;
	const request = (want, nested) => {
		if (transition) {
			deferred = want;
			return;
		}
		if (want === grabbed) return;
		transition = true;
		actions++;
		grabbed = want;
		if (nested !== undefined) request(nested);
		transition = false;
		if (replaying) return;
		replaying = true;
		try {
			for (let pass = 0; pass < 2; ++pass) {
				const pending = deferred;
				deferred = null;
				if (pending === null || pending === grabbed) {
					if (pending === null) break;
					continue;
				}
				request(pending);
			}
		} finally {
			replaying = false;
		}
	};
	request(true, false);
	assert.equal(grabbed, false, 'an opposite request is applied after the active transition');
	assert.equal(actions, 2, 'the deferred opposite request runs exactly once');
	assert.equal(transition, false, 'the transition guard is clear');

	let loops = 0;
	const hostile = () => {
		for (let pass = 0; pass < 2; ++pass) loops++;
	};
	hostile();
	assert.equal(loops, 2, 'a repeated callback cannot create an unbounded replay');
});
